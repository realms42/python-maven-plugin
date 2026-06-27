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

import com.github.realms42.util.CommandRunnerUtil;

abstract class BaseMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project.build.directory}", readonly = true)
    protected File buildDir;

    @Parameter(defaultValue = "${project.basedir}", readonly = true)
    protected File basePath;

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

    protected Path getVenvBinDir() {
        return venvBinDir(getToolsVenvPath());
    }

    private static Path venvBinDir(Path venvPath) {
        String binDir = System.getProperty("os.name").toLowerCase().contains("win") ? "Scripts" : "bin";
        return venvPath.resolve(binDir);
    }

    /** Run a command in the plugin tools venv (ruff, pytest, build). */
    protected void runInVenv(String... cmd) throws MojoExecutionException {
        runInVenv(getToolsVenvPath(), cmd);
    }

    protected void runInVenv(List<String> cmd) throws MojoExecutionException {
        runInVenv(cmd.toArray(new String[0]));
    }

    /** Run a command in the clean project venv (only the project's own deps). */
    protected void runInProjectVenv(String... cmd) throws MojoExecutionException {
        runInVenv(getProjectVenvPath(), cmd);
    }

    /**
     * Run a command in the project work directory with the given venv
     * "activated" via VIRTUAL_ENV and PATH, and the executable resolved to its
     * absolute path inside the venv's bin/Scripts directory. ProcessBuilder
     * resolves a bare program name against the JVM's own PATH rather than the
     * PATH set here, so the absolute path is what makes venv-only tools like
     * ruff or pytest runnable. A command that already carries a path is left
     * untouched, since Path.resolve returns an absolute argument unchanged.
     */
    private void runInVenv(Path venvPath, String... cmd) throws MojoExecutionException {
        Path binDir = venvBinDir(venvPath);
        Map<String, String> env = new HashMap<>();
        env.put("VIRTUAL_ENV", venvPath.toString());
        String existingPath = System.getenv("PATH");
        env.put("PATH", binDir + (existingPath == null ? "" : File.pathSeparator + existingPath));
        List<String> command = new ArrayList<>(Arrays.asList(cmd));
        command.set(0, binDir.resolve(command.get(0)).toString());
        CommandRunnerUtil.run(getWorkPath(), command, env);
    }

}
