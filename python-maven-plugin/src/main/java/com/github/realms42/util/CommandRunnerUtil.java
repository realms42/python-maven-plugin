package com.github.realms42.util;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
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
            // Launch through `env` so the program name resolves against the PATH we
            // inject below. ProcessBuilder resolves the executable against the JVM's
            // own PATH, not the PATH set on its environment map, so venv-only tools
            // like ruff or pytest would otherwise never be found. `env` inherits the
            // child environment and performs its own PATH lookup, which does honor it.
            List<String> command = new ArrayList<>();
            command.add("env");
            command.addAll(cmd);
            ProcessBuilder pb = new ProcessBuilder(command)
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
