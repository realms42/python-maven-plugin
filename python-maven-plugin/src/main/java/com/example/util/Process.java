package com.example.util;

import java.io.IOException;
import java.nio.file.Path;

import org.apache.maven.plugin.MojoExecutionException;

public class Process {
    public static void run(Path workPath, String... command) throws MojoExecutionException {
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
