package com.github.realms42.mojos;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.shared.utils.cli.CommandLineException;
import org.apache.maven.shared.utils.cli.CommandLineUtils;
import org.apache.maven.shared.utils.cli.Commandline;
import org.apache.maven.shared.utils.cli.StreamConsumer;

public abstract class BaseMojo extends AbstractMojo {

    static final String RUFF_VERSION = "0.17.3";
    static final String PYTEST_VERSION = "9.2.0";

    @Parameter(defaultValue = "${project.build.directory}", readonly = true)
    protected File buildDir;

    @Parameter(defaultValue = "${project.basedir}", readonly = true)
    protected File basePath;

    @Parameter(defaultValue = "${project.version}", readonly = true)
    protected String version;

    @Parameter(defaultValue = "${settings.offline}", readonly = true)
    protected boolean offline;

    @Parameter(defaultValue = "${session.request.startTime}", readonly = true)
    protected Date buildStartTime;

    protected Path getPythonMavenPath() {
        return buildDir.toPath().resolve("python-maven-plugin");
    }

    protected Path getPythonBuildPath() {
        return buildDir.toPath().resolve("python-build");
    }

    protected Map<String, String> uvEnvironment() {
        Map<String, String> env = new HashMap<>();
        env.put("UV_PROJECT_ENVIRONMENT", getProjectVenvPath().toString());
        env.put("PYTHONPYCACHEPREFIX", getPythonMavenPath().resolve("pycache").toString());
        env.put("RUFF_CACHE_DIR", getPythonMavenPath().resolve("ruff-cache").toString());
        if (offline) {
            env.put("UV_OFFLINE", "1");
        }
        return env;
    }

    protected Path getProjectVenvPath() {
        return getPythonMavenPath().resolve(".venv");
    }

    // Named .venv so that a project's own ruff skips it, as ruff skips .venv directories by default.
    protected Path getToolsVenvPath() {
        return getPythonMavenPath().resolve("tools").resolve(".venv");
    }

    protected static Path venvExecutable(Path venv, String name) {
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        return windows ? venv.resolve("Scripts").resolve(name + ".exe") : venv.resolve("bin").resolve(name);
    }

    protected void uvSync(String... options) throws MojoExecutionException {
        List<String> args = new ArrayList<>(Arrays.asList("sync", "--locked"));
        args.addAll(Arrays.asList(options));
        run("uv", args, uvEnvironment());
    }

    protected Path installTool(Path venv, String tool, String version) throws MojoExecutionException {
        if (!Files.isDirectory(venv)) {
            run("uv", Arrays.asList("venv", venv.toString()), uvEnvironment());
        }
        run("uv", Arrays.asList("pip", "install", "--python", venv.toString(), tool + "==" + version),
                uvEnvironment());
        return venvExecutable(venv, tool);
    }

    // ruff and pytest exit with 1 when the project fails the check, and with other codes when they can't run.
    protected void runCheck(String tool, String failure, Path executable, List<String> args)
            throws MojoExecutionException, MojoFailureException {
        checkExitCode(tool, failure, exec(executable.toString(), args, uvEnvironment()));
    }

    protected void checkExitCode(String tool, String failure, int exitCode)
            throws MojoExecutionException, MojoFailureException {
        if (exitCode == 1) {
            throw new MojoFailureException(failure);
        }
        if (exitCode != 0) {
            throw new MojoExecutionException(
                    "`" + tool + "` failed with exit code " + exitCode + ". See the output above.");
        }
    }

    protected String ruffExcludeBuildDir() {
        String path = buildDir.getAbsolutePath().replace("\\", "\\\\").replace("\"", "\\\"");
        return "extend-exclude = [\"" + path + "\"]";
    }

    protected void run(String cmd, List<String> args) throws MojoExecutionException {
        run(cmd, args, new HashMap<>());
    }

    protected void run(String cmd, List<String> args, Map<String, String> envOverrides) throws MojoExecutionException {
        int exitCode = exec(cmd, args, envOverrides);
        if (exitCode != 0) {
            String name = args.isEmpty() ? cmd : cmd + " " + args.get(0);
            throw new MojoExecutionException(
                    "`" + name + "` failed with exit code " + exitCode + ". See the output above.");
        }
    }

    protected int exec(String cmd, List<String> args, Map<String, String> envOverrides) throws MojoExecutionException {
        Commandline command = new Commandline();
        command.setExecutable(cmd);
        command.setWorkingDirectory(basePath);
        command.addArguments(args.toArray(String[]::new));

        envOverrides.forEach(command::addEnvironment);

        StreamConsumer stdout = line -> getLog().info(line);
        StreamConsumer stderr = line -> getLog().info(line);
        try {
            getLog().debug("Running command: " + command);
            return CommandLineUtils.executeCommandLine(command, stdout, stderr);
        } catch (CommandLineException e) {
            throw new MojoExecutionException("Failed to log command: " + e.getMessage());
        }
    }

    protected String versionToPep440() throws MojoExecutionException {
        String v = version;
        boolean snapshot = v.regionMatches(true, Math.max(0, v.length() - SNAPSHOT.length()), SNAPSHOT, 0,
                SNAPSHOT.length());
        if (snapshot) {
            v = v.substring(0, v.length() - SNAPSHOT.length());
        }
        Matcher parts = MAVEN_VERSION.matcher(v);
        if (!parts.matches()) {
            throw new MojoExecutionException("Can't convert the Maven version " + version + " to a Python (PEP 440)"
                    + " version: it must start with a numeric release, such as 1.0.0.");
        }
        StringBuilder pep440 = new StringBuilder(parts.group(1));
        String qualifier = parts.group(2) == null ? "" : parts.group(2);
        String local = "";
        Matcher preRelease = PRE_RELEASE.matcher(qualifier);
        if (preRelease.matches()) {
            String label = preRelease.group(1).toLowerCase(Locale.ROOT);
            pep440.append(label.startsWith("a") ? "a" : label.startsWith("b") ? "b" : "rc");
            pep440.append(preRelease.group(2).isEmpty() ? "0" : preRelease.group(2));
        } else if (MILESTONE.matcher(qualifier).matches()) {
            throw new MojoExecutionException("Can't convert the Maven version " + version + " to a Python (PEP 440)"
                    + " version: Python has no milestone releases. Use an alpha, beta or release candidate"
                    + " instead, such as -alpha-1 or -rc-1.");
        } else if (!RELEASE.matcher(qualifier).matches()) {
            local = qualifier.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", ".").replaceAll("^\\.|\\.$", "");
        }
        if (snapshot) {
            Instant start = buildStartTime == null ? Instant.now() : buildStartTime.toInstant();
            pep440.append(".dev").append(SNAPSHOT_TIMESTAMP.format(start));
        }
        if (!local.isEmpty()) {
            pep440.append('+').append(local);
        }
        return pep440.toString();
    }

    private static final String SNAPSHOT = "-SNAPSHOT";
    private static final DateTimeFormatter SNAPSHOT_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);
    private static final Pattern MAVEN_VERSION = Pattern.compile("(\\d+(?:\\.\\d+)*)(?:[.-](.+))?");
    // As in Maven, `a` and `b` only count as alpha and beta when a number follows.
    private static final Pattern PRE_RELEASE =
            Pattern.compile("(alpha|beta|rc|cr|a(?=[.-]?\\d)|b(?=[.-]?\\d))[.-]?(\\d*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern MILESTONE =
            Pattern.compile("(milestone[.-]?\\d*|m[.-]?\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern RELEASE = Pattern.compile("release|final|ga", Pattern.CASE_INSENSITIVE);

}
