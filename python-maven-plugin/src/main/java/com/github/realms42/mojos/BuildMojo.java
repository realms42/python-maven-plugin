package com.github.realms42.mojos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Stream;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Builds the project's sdist and wheel with {@code uv build} into {@code target/python-build/}, using the project's
 * own {@code pyproject.toml} and build backend. The Maven version is passed to the backend as
 * {@code PACKAGE_VERSION}, in PEP 440 form. Distributions from earlier builds are removed first.
 */
@Mojo(name = "build", defaultPhase = LifecyclePhase.COMPILE, threadSafe = true)
public class BuildMojo extends BaseMojo {

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        deleteRecursively(getPythonBuildPath());
        run(
                "uv",
                Arrays.asList("build", "--out-dir", getPythonBuildPath().toString()),
                uvEnvironment());
    }

    // uv build only ever adds to its output directory, so without this a distribution left
    // over from an earlier version would be packaged and published alongside the current one.
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
