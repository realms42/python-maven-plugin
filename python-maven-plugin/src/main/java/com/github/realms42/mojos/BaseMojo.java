package com.github.realms42.mojos;

import java.io.File;
import java.nio.file.Files;
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
        String binDir = System.getProperty("os.name").toLowerCase().contains("win") ? "Scripts" : "bin";
        return getToolsVenvPath().resolve(binDir);
    }

    /**
     * Run a command in the project work directory with the venv "activated" via
     * VIRTUAL_ENV and PATH environment variables. The first PATH entry is the
     * venv's bin/Scripts directory so child processes inherit the venv.
     *
     * <p>The executable itself is resolved to the venv's bin/Scripts directory
     * when a copy exists there. {@link ProcessBuilder} looks up bare command
     * names against the JVM's own PATH rather than the PATH placed in its
     * environment map, so a plain name like "ruff" or "pytest" would otherwise
     * not be found even though it lives in the venv.
     */
    protected void runInVenv(String... cmd) throws MojoExecutionException {
        Map<String, String> env = new HashMap<>();
        env.put("VIRTUAL_ENV", getToolsVenvPath().toString());
        String existingPath = System.getenv("PATH");
        env.put("PATH", getVenvBinDir() + (existingPath == null ? "" : File.pathSeparator + existingPath));
        CommandRunnerUtil.run(getWorkPath(), resolveVenvExecutable(cmd), env);
    }

    /**
     * Replace a bare executable name with its absolute path inside the venv's
     * bin/Scripts directory when present. Commands that already specify a path
     * are left untouched.
     */
    private List<String> resolveVenvExecutable(String... cmd) {
        List<String> resolved = new ArrayList<>(Arrays.asList(cmd));
        if (!resolved.isEmpty()) {
            String program = resolved.get(0);
            if (!program.contains("/") && !program.contains("\\")) {
                Path candidate = getVenvBinDir().resolve(program);
                if (Files.isExecutable(candidate)) {
                    resolved.set(0, candidate.toString());
                }
            }
        }
        return resolved;
    }

    protected void runInVenv(List<String> cmd) throws MojoExecutionException {
        runInVenv(cmd.toArray(new String[0]));
    }

    protected void runInProjectVenv(String... cmd) throws MojoExecutionException {
        String binDir = System.getProperty("os.name").toLowerCase().contains("win") ? "Scripts" : "bin";
        Path projectBinDir = getProjectVenvPath().resolve(binDir);
        Map<String, String> env = new HashMap<>();
        env.put("VIRTUAL_ENV", getProjectVenvPath().toString());
        String existingPath = System.getenv("PATH");
        env.put("PATH", projectBinDir + (existingPath == null ? "" : File.pathSeparator + existingPath));
        CommandRunnerUtil.run(getWorkPath(), Arrays.asList(cmd), env);
    }

}
