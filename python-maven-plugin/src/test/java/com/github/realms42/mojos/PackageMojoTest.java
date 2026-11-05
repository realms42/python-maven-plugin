package com.github.realms42.mojos;

import static com.github.realms42.mojos.MojoTesting.configure;
import static com.github.realms42.mojos.MojoTesting.log;
import static com.github.realms42.mojos.MojoTesting.set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PackageMojoTest {

    @TempDir
    Path project;

    private final MavenProject mavenProject = new MavenProject();
    private final RecordingProjectHelper helper = new RecordingProjectHelper();
    private Path output;

    @BeforeEach
    void setUp() throws IOException {
        mavenProject.setArtifact(new DefaultArtifact(
                "com.example", "example", "1.0.0", null, "python", null, new DefaultArtifactHandler("python")));
        output = Files.createDirectories(project.resolve("target/python-build"));
    }

    private PackageMojo mojo() {
        PackageMojo mojo = configure(new PackageMojo(), project, "1.0.0");
        set(mojo, "project", mavenProject);
        set(mojo, "projectHelper", helper);
        return mojo;
    }

    private Path built(String name) throws IOException {
        return Files.writeString(output.resolve(name), name);
    }

    @Test
    void sdistIsTheProjectArtifactAndWheelsAreAttachedByTheirTag() throws Exception {
        Path sdist = built("example-1.0.0.tar.gz");
        built("example-1.0.0-py3-none-any.whl");
        built("example-1.0.0-cp312-cp312-manylinux_2_17_x86_64.whl");
        built(".gitignore");

        mojo().execute();

        assertEquals(sdist.toFile(), mavenProject.getArtifact().getFile());
        assertEquals(Set.of(
                List.of("whl", "py3-none-any", "example-1.0.0-py3-none-any.whl"),
                List.of("whl", "cp312-cp312-manylinux_2_17_x86_64",
                        "example-1.0.0-cp312-cp312-manylinux_2_17_x86_64.whl")),
                helper.attached);
    }

    @Test
    void wheelBuildNumberIsNotPartOfTheClassifier() throws Exception {
        built("example-1.0.0.tar.gz");
        built("example-1.0.0-1-py3-none-any.whl");

        mojo().execute();

        assertEquals(Set.of(List.of("whl", "py3-none-any", "example-1.0.0-1-py3-none-any.whl")), helper.attached);
    }

    @Test
    void wheelWithoutACompatibilityTagIsAttachedWithoutAClassifier() throws Exception {
        built("example-1.0.0.tar.gz");
        built("example.whl");
        PackageMojo mojo = mojo();

        mojo.execute();

        assertEquals(Set.of(Arrays.asList("whl", null, "example.whl")), helper.attached);
        assertEquals(List.of("Could not read a compatibility tag from example.whl; attached it without a classifier."),
                log(mojo).warnings);
    }

    @Test
    void publishesOnlyTheSdistWhenNoWheelWasBuilt() throws Exception {
        built("example-1.0.0.tar.gz");
        PackageMojo mojo = mojo();

        mojo.execute();

        assertTrue(helper.attached.isEmpty());
        assertEquals(List.of("No .whl was built; only the sdist will be published."), log(mojo).warnings);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void failsUnlessThereIsExactlyOneSdist(int sdists) throws Exception {
        built("example-1.0.0-py3-none-any.whl");
        for (int i = 0; i < sdists; i++) {
            built("example-1.0." + i + ".tar.gz");
        }

        MojoExecutionException e = assertThrows(MojoExecutionException.class, () -> mojo().execute());

        assertEquals("Expected exactly one .tar.gz in " + output + ", found " + sdists
                + ". Maven can only publish one primary artifact.", e.getMessage());
    }

    @Test
    void failsWhenNothingWasBuilt() throws Exception {
        Files.delete(output);

        MojoExecutionException e = assertThrows(MojoExecutionException.class, () -> mojo().execute());

        assertEquals("Failed to list files in " + output, e.getMessage());
    }

    private static final class RecordingProjectHelper implements MavenProjectHelper {
        final Set<List<String>> attached = new HashSet<>();

        @Override
        public void attachArtifact(MavenProject project, String type, String classifier, File file) {
            attached.add(Arrays.asList(type, classifier, file.getName()));
        }

        @Override
        public void attachArtifact(MavenProject project, String type, File file) {
            attached.add(Arrays.asList(type, null, file.getName()));
        }

        @Override
        public void attachArtifact(MavenProject project, File file, String classifier) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addResource(MavenProject project, String directory, List<String> includes, List<String> excludes) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addTestResource(MavenProject project, String directory, List<String> includes,
                List<String> excludes) {
            throw new UnsupportedOperationException();
        }
    }
}
