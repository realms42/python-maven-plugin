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

    protected Path getPythonMavenPath() {
        return buildDir.toPath().resolve("python-maven-plugin");
    }

    protected Path getPythonBuildPath() {
        return buildDir.toPath().resolve("python-build");
    }

    // The virtualenv, bytecode and tool caches all go under target/, so a build never writes into the project.
    protected Map<String, String> uvEnvironment() {
        return Map.of(
                "PACKAGE_VERSION", versionToPep440(),
                "UV_PROJECT_ENVIRONMENT", getPythonMavenPath().resolve(".venv").toString(),
                "PYTHONPYCACHEPREFIX", getPythonMavenPath().resolve("pycache").toString(),
                "RUFF_CACHE_DIR", getPythonMavenPath().resolve("ruff-cache").toString());
    }

    // `--with tool` defers to the project's own pin of the tool, if it has one, and uses the
    // latest release otherwise. `--locked` fails on a missing or stale uv.lock instead of writing it.
    protected List<String> uvRun(String tool, String... args) {
        List<String> command = new ArrayList<>(Arrays.asList("run", "--locked", "--with", tool, tool));
        command.addAll(Arrays.asList(args));
        return command;
    }

    protected void run(String cmd, List<String> args) throws MojoExecutionException {
        run(cmd, args, new HashMap<>());
    }

    protected void run(String cmd, List<String> args, Map<String, String> envOverrides) throws MojoExecutionException {
        int exitCode = exec(cmd, args, envOverrides);
        if (exitCode != 0) {
            throw new MojoExecutionException("Command failed with exit code " + exitCode);
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
            getLog().info("Running command: " + command);
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
