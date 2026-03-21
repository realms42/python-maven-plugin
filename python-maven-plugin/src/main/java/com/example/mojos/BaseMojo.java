package com.example.mojos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;

abstract class BaseMojo extends AbstractMojo {
    @Parameter(defaultValue = "${project.build.directory}/python-maven-plugin", readonly = true)
    protected Path buildPath;

    @Parameter(defaultValue = "${project.basedir}", readonly = true)
    protected Path basePath;

    protected Path venvPath = buildPath.resolve("venv");

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (!Files.exists(basePath)) {
            throw new MojoExecutionException("Base directory " + basePath + " does not exist");
        }
        if (!Files.exists(buildPath)) {
            try {
                Files.createDirectories(buildPath);
            } catch (IOException e) {
                throw new MojoExecutionException(
                        "Failed to create build directory " + buildPath + ": " + e.getMessage());
            }
        }
    }

    protected Path venvBin(String executable) {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        String bin = isWindows ? "Scripts" : "bin";
        return venvPath.resolve(bin).resolve(executable);
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
