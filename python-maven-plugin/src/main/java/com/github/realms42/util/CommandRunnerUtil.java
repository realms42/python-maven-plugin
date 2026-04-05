package com.github.realms42.util;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;

public class CommandRunnerUtil {
    public static void run(Path workPath, List<String> cmd) throws MojoExecutionException {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd)
                    .directory(workPath.toFile())
                    .inheritIO();
            int exit = pb.start().waitFor();
            if (exit != 0) {
                throw new MojoExecutionException(
                        "Command failed with exit code " + exit + ": " + String.join(" ", cmd));
            }
        } catch (IOException | InterruptedException e) {
            throw new MojoExecutionException("Failed to run command: " + e.getMessage());
        }
    }
}
