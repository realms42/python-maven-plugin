package com.github.realms42.mojos;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

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
 * <p>The wheel is the project's own artifact — it is the installable form, so
 * it is what a dependent project resolves. The sdist is attached alongside it
 * under the "sources" classifier. Without this, install and deploy have
 * nothing to publish and the packaging type is decorative.
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

        Path wheel = sole(artifacts, WHEEL);
        project.getArtifact().setFile(wheel.toFile());
        getLog().info("Project artifact: " + wheel.getFileName());

        Optional<Path> sdist = find(artifacts, SDIST);
        if (sdist.isPresent()) {
            projectHelper.attachArtifact(project, "tar.gz", "sources", sdist.get().toFile());
            getLog().info("Attached as sources: " + sdist.get().getFileName());
        } else {
            getLog().warn("No " + SDIST + " to attach; only the wheel will be installed.");
        }
    }

    /** The one artifact with the given suffix — anything else leaves Maven nothing coherent to install. */
    private Path sole(List<Path> artifacts, String suffix) throws MojoExecutionException {
        List<Path> matches = matching(artifacts, suffix);
        if (matches.size() != 1) {
            throw new MojoExecutionException("Expected exactly one " + suffix + " in " + getBuildPath()
                    + ", found " + matches.size() + ". Maven can only publish one primary artifact.");
        }
        return matches.get(0);
    }

    private Optional<Path> find(List<Path> artifacts, String suffix) {
        List<Path> matches = matching(artifacts, suffix);
        return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
    }

    private static List<Path> matching(List<Path> artifacts, String suffix) {
        return artifacts.stream()
                .filter(p -> p.getFileName().toString().endsWith(suffix))
                .collect(java.util.stream.Collectors.toList());
    }
}
