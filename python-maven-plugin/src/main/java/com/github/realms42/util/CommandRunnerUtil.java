package com.github.realms42.util;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.apache.maven.plugin.MojoExecutionException;

public class CommandRunnerUtil {
    public static void run(Path workPath, List<String> cmd) throws MojoExecutionException {
        run(workPath, cmd, null);
    }

    public static void run(Path workPath, List<String> cmd, Map<String, String> envOverrides)
            throws MojoExecutionException {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd)
                    .directory(workPath.toFile())
                    .inheritIO();
            if (envOverrides != null) {
                pb.environment().putAll(envOverrides);
            }
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
