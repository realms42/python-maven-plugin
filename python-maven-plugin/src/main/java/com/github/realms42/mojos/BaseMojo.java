package com.github.realms42.mojos;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
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
        env.put("PACKAGE_VERSION", versionToPep440());
        env.put("UV_PROJECT_ENVIRONMENT", getPythonMavenPath().resolve(".venv").toString());
        env.put("PYTHONPYCACHEPREFIX", getPythonMavenPath().resolve("pycache").toString());
        env.put("RUFF_CACHE_DIR", getPythonMavenPath().resolve("ruff-cache").toString());
        if (offline) {
            env.put("UV_OFFLINE", "1");
        }
        return env;
    }

    // Sets up the project's virtualenv before a tool runs in it. `--locked` fails on a missing or stale uv.lock
    // instead of writing it. Done as its own step so that a uv failure, including a project that fails to build
    // (which uv reports with exit code 1), is never mistaken for the tool's own exit code 1.
    protected void uvSync() throws MojoExecutionException {
        run("uv", Arrays.asList("sync", "--locked"), uvEnvironment());
    }

    // Runs a tool in the virtualenv set up by uvSync(). `--with tool` defers to the project's own pin of the
    // tool, if it has one, and uses the latest release otherwise.
    protected List<String> uvRun(String tool, String... args) {
        List<String> command = new ArrayList<>(Arrays.asList("run", "--no-sync", "--with", tool, tool));
        command.addAll(Arrays.asList(args));
        return command;
    }

    // Runs a check such as ruff or pytest. These exit with 1 when the project fails the check, which is a build
    // failure (the project's fault), and with other codes when they can't run, which is a build error.
    protected void runCheck(String tool, String failure, List<String> uvArgs)
            throws MojoExecutionException, MojoFailureException {
        checkExitCode(tool, failure, exec("uv", uvArgs, uvEnvironment()));
    }

    protected void checkExitCode(String tool, String failure, int exitCode)
            throws MojoExecutionException, MojoFailureException {
        if (exitCode == 1) {
            throw new MojoFailureException(failure);
        }
        if (exitCode != 0) {
            throw new MojoExecutionException(
                    "`uv run " + tool + "` failed with exit code " + exitCode + ". See the output above.");
        }
    }

    // Keeps ruff out of Maven's build directory, whether or not the project's .gitignore lists it. A --config
    // override adds to the project's own extend-exclude on every ruff since 0.2.2, whereas
    // `ruff format --extend-exclude` only exists since 0.15.21, newer than a project may have pinned.
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
