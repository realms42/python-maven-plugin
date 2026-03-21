package com.example.mojos;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;

abstract class BaseMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project.build.directory}", readonly = true)
    protected File buildDir;

    @Parameter(defaultValue = "${project.basedir}", readonly = true)
    protected File basePath;

    protected Path getPythonBuildPath() {
        return buildDir.toPath().resolve("python-maven-plugin");
    }

    protected Path getVenvPath() {
        return getPythonBuildPath().resolve("venv");
    }

    protected Path venvBin(String executable) {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        String bin = isWindows ? "Scripts" : "bin";
        return getVenvPath().resolve(bin).resolve(executable);
    }

    protected void runProcess(Path workPath, String... command) throws MojoExecutionException {
        try {
            int exit = new ProcessBuilder(command)
                    .directory(workPath.toFile())
                    .inheritIO()
                    .start()
                    .waitFor();
            if (exit != 0) {
                throw new MojoExecutionException(
                        "Command failed with exit code " + exit + ": " + String.join(" ", command));
            }
        } catch (IOException | InterruptedException e) {
            throw new MojoExecutionException("Failed to run command: " + e.getMessage());
        }
    }

}
