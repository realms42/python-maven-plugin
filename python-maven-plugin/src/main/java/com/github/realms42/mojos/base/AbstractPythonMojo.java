package com.github.realms42.mojos.base;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;

import com.github.realms42.util.CommandRunnerUtil;

/**
 * Common ground for every goal in this plugin: the layout of the build
 * directory, the two virtual environments, and the ability to run a tool
 * inside one of them.
 *
 * <p>
 * Goals that do nothing but run a single tool should extend
 * {@link AbstractVenvCommandMojo} instead of this class directly.
 */
public abstract class AbstractPythonMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project.build.directory}", readonly = true)
    protected File buildDir;

    @Parameter(defaultValue = "${project.basedir}", readonly = true)
    protected File basePath;

    @Parameter(defaultValue = "python3")
    protected String python;

    protected Path getPythonMavenPath() {
        return buildDir.toPath().resolve("python-maven-plugin");
    }

    protected Path getWorkPath() {
        return getPythonMavenPath().resolve("work");
    }

    protected Path getBuildPath() {
        return getPythonMavenPath().resolve("build");
    }

    protected Path getProjectVenvPath() {
        return getWorkPath().resolve(".venv");
    }

    protected Path getPluginVenvPath() {
        return getPythonMavenPath().resolve(".venv");
    }

    private static Path venvBinDir(Path venvPath) {
        String binDir = System.getProperty("os.name").toLowerCase().contains("win") ? "Scripts" : "bin";
        return venvPath.resolve(binDir);
    }

    protected void runInVenv(String program, String... args) throws MojoExecutionException {
        runInVenv(getPluginVenvPath(), commandOf(program, args), Collections.emptyMap());
    }

    protected void runInVenv(List<String> command) throws MojoExecutionException {
        runInVenv(getPluginVenvPath(), command, Collections.emptyMap());
    }

    protected void runInVenv(List<String> command, Map<String, String> extraEnv) throws MojoExecutionException {
        runInVenv(getPluginVenvPath(), command, extraEnv);
    }

    protected void runInProjectVenv(String program, String... args) throws MojoExecutionException {
        runInVenv(getProjectVenvPath(), commandOf(program, args), Collections.emptyMap());
    }

    private static List<String> commandOf(String program, String... args) {
        List<String> command = new ArrayList<>();
        command.add(program);
        command.addAll(Arrays.asList(args));
        return command;
    }

    private void runInVenv(Path venvPath, List<String> command, Map<String, String> extraEnv)
            throws MojoExecutionException {
        if (command.isEmpty()) {
            throw new MojoExecutionException("No command given to run in " + venvPath);
        }
        Path binDir = venvBinDir(venvPath);
        Map<String, String> env = new HashMap<>();
        env.put("VIRTUAL_ENV", venvPath.toString());
        String existingPath = System.getenv("PATH");
        env.put("PATH", binDir + (existingPath == null ? "" : File.pathSeparator + existingPath));
        env.putAll(extraEnv);
        List<String> resolved = new ArrayList<>(command);
        resolved.set(0, binDir.resolve(command.get(0)).toString());
        CommandRunnerUtil.run(getWorkPath(), resolved, env);
    }

}
