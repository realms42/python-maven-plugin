package com.github.realms42.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
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
            ProcessBuilder pb = new ProcessBuilder()
                    .directory(workPath.toFile())
                    .inheritIO();
            if (envOverrides != null) {
                pb.environment().putAll(envOverrides);
            }
            pb.command(resolveProgram(cmd, pb.environment().get("PATH")));
            int exit = pb.start().waitFor();
            if (exit != 0) {
                throw new MojoExecutionException(
                        "Command failed with exit code " + exit + ": " + String.join(" ", cmd));
            }
        } catch (IOException | InterruptedException e) {
            throw new MojoExecutionException("Failed to run command: " + e.getMessage());
        }
    }

    /**
     * Resolve a bare program name against the supplied PATH. {@link ProcessBuilder}
     * looks executables up against the JVM's own PATH rather than the PATH placed
     * in its environment map, so a venv-only tool like "ruff" would not be found
     * even after the venv's bin directory is prepended to PATH. Commands that
     * already specify a path, or whose program is not found on PATH, are returned
     * unchanged so the OS lookup still applies.
     */
    private static List<String> resolveProgram(List<String> cmd, String path) {
        if (cmd.isEmpty() || path == null) {
            return cmd;
        }
        String program = cmd.get(0);
        if (program.contains("/") || program.contains("\\")) {
            return cmd;
        }
        for (String dir : path.split(File.pathSeparator)) {
            if (dir.isEmpty()) {
                continue;
            }
            Path candidate = new File(dir, program).toPath();
            if (Files.isExecutable(candidate)) {
                List<String> resolved = new ArrayList<>(cmd);
                resolved.set(0, candidate.toString());
                return resolved;
            }
        }
        return cmd;
    }
}
