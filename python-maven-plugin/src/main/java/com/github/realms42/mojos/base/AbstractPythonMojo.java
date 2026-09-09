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
 * <p>Goals that do nothing but run a single tool should extend
 * {@link AbstractVenvCommandMojo} instead of this class directly.
 */
public abstract class AbstractPythonMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project.build.directory}", readonly = true)
    protected File buildDir;

    @Parameter(defaultValue = "${project.basedir}", readonly = true)
    protected File basePath;

    /**
     * Interpreter used to create the venvs and to drive tools that are invoked as
     * {@code python -m ...}. Declared once here so every goal agrees on it.
     */
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

    /** Clean project venv — only the user's declared dependencies, no plugin tooling. */
    protected Path getProjectVenvPath() {
        return getWorkPath().resolve(".venv");
    }

    /**
     * Plugin tools venv — contains pytest, ruff, build, and an editable install of
     * the project so tests can import it. Kept outside work/ so it is never packaged.
     */
    protected Path getToolsVenvPath() {
        return getPythonMavenPath().resolve("tools");
    }

    private static Path venvBinDir(Path venvPath) {
        String binDir = System.getProperty("os.name").toLowerCase().contains("win") ? "Scripts" : "bin";
        return venvPath.resolve(binDir);
    }

    /** Run a command in the plugin tools venv (ruff, pytest, build). */
    protected void runInVenv(String program, String... args) throws MojoExecutionException {
        runInVenv(getToolsVenvPath(), commandOf(program, args), Collections.emptyMap());
    }

    /** Run a pre-assembled command — program name first — in the plugin tools venv. */
    protected void runInVenv(List<String> command) throws MojoExecutionException {
        runInVenv(getToolsVenvPath(), command, Collections.emptyMap());
    }

    /**
     * Run a command in the plugin tools venv with extra environment variables.
     * Intended for secrets: anything passed here stays out of the argument list,
     * which is echoed on failure and visible in the process table.
     */
    protected void runInVenv(List<String> command, Map<String, String> extraEnv) throws MojoExecutionException {
        runInVenv(getToolsVenvPath(), command, extraEnv);
    }

    /** Run a command in the clean project venv (only the project's own deps). */
    protected void runInProjectVenv(String program, String... args) throws MojoExecutionException {
        runInVenv(getProjectVenvPath(), commandOf(program, args), Collections.emptyMap());
    }

    private static List<String> commandOf(String program, String... args) {
        List<String> command = new ArrayList<>();
        command.add(program);
        command.addAll(Arrays.asList(args));
        return command;
    }

    /**
     * Run a bare venv command in the project work directory. The first element of
     * {@code command} is a plain executable name such as "ruff" or "pytest"; it is
     * run from the given venv's bin/Scripts directory, with the remaining elements
     * passed through unchanged. The venv is also "activated" via VIRTUAL_ENV and PATH.
     *
     * <p>ProcessBuilder resolves a bare name against the JVM's own PATH rather
     * than the PATH set here, so pinning the program to the venv's bin dir is
     * what makes venv-only tools runnable. This intentionally supports bare
     * commands only — callers pass the tool name, never a path to it.
     */
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
