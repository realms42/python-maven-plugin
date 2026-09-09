package com.github.realms42.mojos;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;

import com.github.realms42.mojos.base.AbstractPythonMojo;
import com.github.realms42.util.FileCopyUtil;

/**
 * Copies the built distributions into the build directory and hands them to
 * Maven as this project's artifacts.
 *
 * <p>The sdist is the project's artifact. It is the one distribution that fits
 * Maven's model: it carries no platform tags, so a single set of coordinates is
 * correct for every consumer, and pip reads its metadata from inside the
 * archive rather than from the file name Maven gives it.
 *
 * <p>Wheels are attached alongside it, each under its compatibility tag as the
 * classifier. Maven coordinates have nowhere else to record that tag, so
 * without it platform-specific wheels would collide on one set of coordinates
 * and a consumer could resolve a wheel built for another platform.
 */
@Mojo(name = "package", defaultPhase = LifecyclePhase.PACKAGE)
public class PackageMojo extends AbstractPythonMojo {

    private static final String WHEEL = ".whl";
    private static final String SDIST = ".tar.gz";

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Component
    private MavenProjectHelper projectHelper;

    @Override
    public void execute() throws MojoExecutionException {
        Path target = buildDir.toPath();
        List<Path> artifacts;
        try {
            artifacts = FileCopyUtil.copyFiles(getBuildPath(), p -> target.resolve(p.getFileName()));
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to package artifacts: " + e.getMessage());
        }
        for (Path artifact : artifacts) {
            getLog().info("Packaged: " + artifact.getFileName());
        }

        project.getArtifact().setFile(soleSdist(artifacts).toFile());
        getLog().info("Project artifact: " + project.getArtifact().getFile().getName());

        attachWheels(matching(artifacts, WHEEL));
    }

    /** Maven publishes one primary artifact, so the build has to have produced exactly one sdist. */
    private Path soleSdist(List<Path> artifacts) throws MojoExecutionException {
        List<Path> matches = matching(artifacts, SDIST);
        if (matches.size() != 1) {
            throw new MojoExecutionException("Expected exactly one " + SDIST + " in " + getBuildPath()
                    + ", found " + matches.size() + ". Maven can only publish one primary artifact.");
        }
        return matches.get(0);
    }

    private void attachWheels(List<Path> wheels) {
        if (wheels.isEmpty()) {
            getLog().warn("No " + WHEEL + " was built; only the sdist will be published.");
            return;
        }
        for (Path wheel : wheels) {
            Optional<String> tag = compatibilityTag(wheel);
            if (tag.isPresent()) {
                projectHelper.attachArtifact(project, "whl", tag.get(), wheel.toFile());
                getLog().info("Attached wheel: " + wheel.getFileName() + " (classifier " + tag.get() + ")");
            } else {
                projectHelper.attachArtifact(project, "whl", wheel.toFile());
                getLog().warn("Could not read a compatibility tag from " + wheel.getFileName()
                        + "; attached it without a classifier.");
            }
        }
    }

    /**
     * The trailing python-abi-platform tag of a wheel file name, which is what
     * decides where the wheel may be installed. PEP 427 names a wheel
     * {@code name-version(-build)?-python-abi-platform.whl}, so it is the last
     * three hyphen-separated fields.
     */
    private static Optional<String> compatibilityTag(Path wheel) {
        String name = wheel.getFileName().toString();
        String[] fields = name.substring(0, name.length() - WHEEL.length()).split("-");
        if (fields.length < 5) {
            return Optional.empty();
        }
        return Optional.of(String.join("-", Arrays.copyOfRange(fields, fields.length - 3, fields.length)));
    }

    private static List<Path> matching(List<Path> artifacts, String suffix) {
        return artifacts.stream()
                .filter(p -> p.getFileName().toString().endsWith(suffix))
                .collect(Collectors.toList());
    }
}
