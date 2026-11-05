package com.github.realms42.mojos;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.shared.utils.cli.CommandLineException;
import org.apache.maven.shared.utils.cli.CommandLineUtils;
import org.apache.maven.shared.utils.cli.Commandline;
import org.apache.maven.shared.utils.cli.StreamConsumer;

public abstract class BaseMojo extends AbstractMojo {

    // The tool versions the plugin runs under Maven, whatever a project pins for its own use. Changing them is
    // a deliberate plugin release, so a new ruff or pytest release can't break a build that changed nothing.
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

    protected Path getPythonMavenPath() {
        return buildDir.toPath().resolve("python-maven-plugin");
    }

    protected Path getPythonBuildPath() {
        return buildDir.toPath().resolve("python-build");
    }

    // The virtualenv, bytecode and tool caches all go under target/, so a build never writes into the project.
    // In Maven's offline mode (-o), uv stays offline too and works from its cache.
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

    // The plugin's own environment for the project: the project and its locked dependencies, under target/.
    // A project's own .venv is never used or touched.
    protected Path getProjectVenvPath() {
        return getPythonMavenPath().resolve(".venv");
    }

    // The plugin's own environment for tools that don't need the project, such as ruff. Named .venv, like the
    // project's, because ruff and other tools skip directories with that name by default: a project running its
    // own ruff must not lint the plugin's environments, even when target/ isn't in its .gitignore.
    protected Path getToolsVenvPath() {
        return getPythonMavenPath().resolve("tools").resolve(".venv");
    }

    protected static Path venvExecutable(Path venv, String name) {
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        return windows ? venv.resolve("Scripts").resolve(name + ".exe") : venv.resolve("bin").resolve(name);
    }

    // Sets up the plugin's environment for the project. `--locked` fails on a missing or stale uv.lock instead
    // of writing it.
    protected void uvSync() throws MojoExecutionException {
        run("uv", Arrays.asList("sync", "--locked"), uvEnvironment());
    }

    // Installs the plugin's pinned version of a tool into one of its environments, replacing any other version
    // there, such as one the project's lock pins. Creates the environment first if needed.
    protected Path installTool(Path venv, String tool, String version) throws MojoExecutionException {
        if (!Files.isDirectory(venv)) {
            run("uv", Arrays.asList("venv", venv.toString()), uvEnvironment());
        }
        run("uv", Arrays.asList("pip", "install", "--python", venv.toString(), tool + "==" + version),
                uvEnvironment());
        return venvExecutable(venv, tool);
    }

    // Runs a check such as ruff or pytest straight from its environment, so its exit code is its own and never
    // uv's. These exit with 1 when the project fails the check, which is a build failure (the project's fault),
    // and with other codes when they can't run, which is a build error.
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

    // Keeps ruff out of Maven's build directory, whether or not the project's .gitignore lists it. Passed as a
    // --config override, which adds to the project's own extend-exclude.
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

    protected String versionToPep440() {
        String v = version.replaceAll("(?i)[.\\-](RELEASE|Final|GA)$", "");
        v = v.replaceAll("(?i)-SNAPSHOT$", ".dev0");
        v = v.replaceAll("(?i)-alpha-?(\\d+)$", "a$1");
        v = v.replaceAll("(?i)-beta-?(\\d+)$", "b$1");
        v = v.replaceAll("(?i)-rc-?(\\d+)$", "rc$1");
        return v;
    }

}
