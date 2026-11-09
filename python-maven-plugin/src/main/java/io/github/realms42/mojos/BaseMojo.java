package io.github.realms42.mojos;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    interface CommandRunner {
        int run(String cmd, List<String> args, Map<String, String> env) throws MojoExecutionException;
    }

    // Where commands run; the tests replace it to record them.
    CommandRunner commandRunner = this::runProcess;

    protected Path getPythonMavenPath() {
        return buildDir.toPath().resolve("python-maven-plugin");
    }

    protected Path getPythonBuildPath() {
        return buildDir.toPath().resolve("python-build");
    }

    // The sdists and wheels the build goal built, sorted; none when it hasn't run.
    protected List<Path> builtDistributions() throws MojoExecutionException {
        Path output = getPythonBuildPath();
        if (!Files.isDirectory(output)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(output)) {
            return files.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(WHEEL)
                            || p.getFileName().toString().endsWith(SDIST))
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to list " + output, e);
        }
    }

    protected List<Path> built(String suffix) throws MojoExecutionException {
        return builtDistributions().stream()
                .filter(p -> p.getFileName().toString().endsWith(suffix))
                .collect(Collectors.toList());
    }

    protected boolean isSnapshot() {
        return Versions.isSnapshot(version);
    }

    protected String versionToPep440() throws MojoExecutionException {
        return Versions.toPep440(version, buildStartTime == null ? Instant.now() : buildStartTime.toInstant());
    }

    // A goal's name = value settings, sorted by name and trimmed, so that options come out in a stable order.
    protected static List<Map.Entry<String, String>> settings(Map<String, String> settings) {
        List<Map.Entry<String, String>> sorted = new ArrayList<>();
        new TreeMap<>(settings == null ? Map.<String, String>of() : settings).forEach((name, value) ->
                sorted.add(new SimpleImmutableEntry<>(name.trim(), value == null ? "" : value.trim())));
        return sorted;
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
        return venv.resolve("bin").resolve(name);
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
        pipInstall(venv, tool + "==" + version);
        return venvExecutable(venv, tool);
    }

    protected void pipInstall(Path venv, String... args) throws MojoExecutionException {
        List<String> command = new ArrayList<>(Arrays.asList("pip", "install", "--python", venv.toString()));
        command.addAll(Arrays.asList(args));
        run("uv", command, uvEnvironment());
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
            throw commandFailed(tool, exitCode);
        }
    }

    private static MojoExecutionException commandFailed(String command, int exitCode) {
        return new MojoExecutionException(
                "`" + command + "` failed with exit code " + exitCode + ". See the output above.");
    }

    protected String ruffExcludeBuildDir() {
        String path = buildDir.getAbsolutePath().replace("\\", "\\\\").replace("\"", "\\\"");
        return "extend-exclude = [\"" + path + "\"]";
    }

    protected void run(String cmd, List<String> args, Map<String, String> envOverrides) throws MojoExecutionException {
        int exitCode = exec(cmd, args, envOverrides);
        if (exitCode != 0) {
            throw commandFailed(args.isEmpty() ? cmd : cmd + " " + args.get(0), exitCode);
        }
    }

    protected int exec(String cmd, List<String> args, Map<String, String> envOverrides) throws MojoExecutionException {
        return commandRunner.run(cmd, args, envOverrides);
    }

    private int runProcess(String cmd, List<String> args, Map<String, String> envOverrides)
            throws MojoExecutionException {
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
            throw new MojoExecutionException("Failed to run `" + cmd + "`: " + e.getMessage(), e);
        }
    }

    protected static final String WHEEL = ".whl";
    protected static final String SDIST = ".tar.gz";

}
