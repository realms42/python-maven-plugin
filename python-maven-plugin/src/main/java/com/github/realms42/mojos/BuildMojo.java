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

/**
 * Builds the project's sdist and wheel with {@code uv build} into {@code target/python-build/}, using the project's
 * own {@code pyproject.toml} and build backend. Distributions from earlier builds are removed first.
 *
 * <p>The distributions get the Maven version, in PEP 440 form, through the variables that version-from-SCM
 * backends already read: {@code SETUPTOOLS_SCM_PRETEND_VERSION} (hatch-vcs, setuptools-scm) and
 * {@code PDM_BUILD_SCM_VERSION} (pdm-backend). Outside Maven, the project's own versioning applies.
 */
@Mojo(name = "build", defaultPhase = LifecyclePhase.COMPILE, threadSafe = true)
public class BuildMojo extends BaseMojo {

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        String pep440 = versionToPep440();
        getLog().info("Python version: " + pep440 + " (from Maven version " + version + ")");
        deleteRecursively(getPythonBuildPath());
        run("uv", Arrays.asList("build", "--out-dir", getPythonBuildPath().toString()), buildEnvironment(pep440));
    }

    // Only for `uv build`, which builds the project alone. These variables apply to every package a backend
    // builds, so setting them for `uv sync` would also give the project's version to any dependency built from
    // source there, such as a path or git dependency.
    private Map<String, String> buildEnvironment(String pep440) {
        Map<String, String> env = uvEnvironment();
        env.put("SETUPTOOLS_SCM_PRETEND_VERSION", pep440);
        env.put("PDM_BUILD_SCM_VERSION", pep440);
        return env;
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
