package com.realms42.util;

import java.io.IOException;
import java.nio.file.Path;

import org.apache.maven.plugin.MojoExecutionException;

public class CommandRunner {
    public static void run(Path workPath, String... command) throws MojoExecutionException {
        try {
            ProcessBuilder pb = new ProcessBuilder(command)
                    .directory(workPath.toFile())
                    .inheritIO();
            int exit = pb.start().waitFor();
            if (exit != 0) {
                throw new MojoExecutionException(
                        "Command failed with exit code " + exit + ": " + String.join(" ", command));
            }
        } catch (IOException | InterruptedException e) {
            throw new MojoExecutionException("Failed to run command: " + e.getMessage());
        }
    }

}
