package com.github.realms42.mojos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/** Builds the sdist and wheel with {@code uv build}, versioned from the Maven version. */
@Mojo(name = "build", defaultPhase = LifecyclePhase.COMPILE, threadSafe = true)
public class BuildMojo extends BaseMojo {

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        String pep440 = versionToPep440();
        getLog().info("Python version: " + pep440 + " (from Maven version " + version + ")");
        deleteRecursively(getPythonBuildPath());
        run("uv", Arrays.asList("build", "--out-dir", getPythonBuildPath().toString()), buildEnvironment(pep440));
    }

    // Not for `uv sync`: backends apply these to every package they build, dependencies included.
    private Map<String, String> buildEnvironment(String pep440) {
        Map<String, String> env = uvEnvironment();
        env.put("SETUPTOOLS_SCM_PRETEND_VERSION", pep440);
        env.put("PDM_BUILD_SCM_VERSION", pep440);
        return env;
    }

    private static void deleteRecursively(Path directory) throws MojoExecutionException {
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) {
                Files.delete(path);
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to clean " + directory, e);
        }
    }
}
