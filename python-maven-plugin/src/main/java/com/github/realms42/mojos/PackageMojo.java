package com.github.realms42.mojos;

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

/** Attaches the sdist as the main artifact and each wheel with its tag as classifier. */
@Mojo(name = "package", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public class PackageMojo extends BaseMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Component
    private MavenProjectHelper projectHelper;

    @Override
    public void execute() throws MojoExecutionException {
        List<Path> artifacts = builtDistributions();
        project.getArtifact().setFile(soleSdist(artifacts).toFile());
        getLog().info("Project artifact: " + project.getArtifact().getFile().getName());

        attachWheels(matching(artifacts, WHEEL));
    }

    private Path soleSdist(List<Path> artifacts) throws MojoExecutionException {
        List<Path> matches = matching(artifacts, SDIST);
        if (matches.size() != 1) {
            throw new MojoExecutionException("Expected exactly one " + SDIST + " in " + getPythonBuildPath()
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
