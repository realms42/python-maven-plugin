package com.github.realms42.mojos;

import java.io.File;
import java.nio.file.Path;
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

    protected Path getVenvPath() {
        return getWorkPath().resolve(".venv");
    }

    protected Path getVenvBinDir() {
        String binDir = System.getProperty("os.name").toLowerCase().contains("win") ? "Scripts" : "bin";
        return getVenvPath().resolve(binDir);
    }

    /**
     * Run a command in the project work directory with the venv "activated" via
     * VIRTUAL_ENV and PATH environment variables. The first PATH entry is the
     * venv's bin/Scripts directory, so plain executable names like "ruff" or
     * "pytest" resolve to the venv's copies.
     */
    protected void runInVenv(String... cmd) throws MojoExecutionException {
        Map<String, String> env = new HashMap<>();
        env.put("VIRTUAL_ENV", getVenvPath().toString());
        String existingPath = System.getenv("PATH");
        env.put("PATH", getVenvBinDir() + (existingPath == null ? "" : File.pathSeparator + existingPath));
        CommandRunnerUtil.run(getWorkPath(), Arrays.asList(cmd), env);
    }

    protected void runInVenv(List<String> cmd) throws MojoExecutionException {
        runInVenv(cmd.toArray(new String[0]));
    }

}
