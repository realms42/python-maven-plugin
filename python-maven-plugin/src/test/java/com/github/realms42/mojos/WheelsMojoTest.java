package com.github.realms42.mojos;

import static com.github.realms42.mojos.MojoTesting.configure;
import static com.github.realms42.mojos.MojoTesting.log;
import static com.github.realms42.mojos.MojoTesting.set;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class WheelsMojoTest {

    @TempDir
    Path project;

    private final Set<Artifact> artifacts = new LinkedHashSet<>();
    private final MavenProject mavenProject = new MavenProject();

    private Path output() {
        return project.resolve("target/wheels");
    }

    private WheelsMojo mojo() {
        mavenProject.setArtifacts(artifacts);
        WheelsMojo mojo = configure(new WheelsMojo(), project, "1.0.0-SNAPSHOT");
        set(mojo, "project", mavenProject);
        set(mojo, "outputDirectory", output().toFile());
        return mojo;
    }

    private Path wheel(String file, Map<String, String> entries) throws IOException {
        Path path = Files.createDirectories(project.resolve("repo")).resolve(file);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return path;
    }

    private Path wheel(String file, String name, String version, String... tags) throws IOException {
        String distInfo = name.replace('-', '_') + "-" + version + ".dist-info/";
        StringBuilder wheelFile = new StringBuilder("Wheel-Version: 1.0\nGenerator: test\nRoot-Is-Purelib: true\n");
        for (String tag : tags) {
            wheelFile.append("Tag: ").append(tag).append('\n');
        }
        return wheel(file, Map.of(
                "pkg/__init__.py", "",
                distInfo + "METADATA", "Metadata-Version: 2.4\nName: " + name + "\nVersion: " + version
                        + "\nSummary: A test\n\nName: not-a-header\n",
                distInfo + "WHEEL", wheelFile.toString()));
    }

    private Artifact dependency(String artifactId, String version, String type, String classifier, Path file) {
        Artifact artifact = new DefaultArtifact("com.example", artifactId, version, "compile", type, classifier,
                new DefaultArtifactHandler(type));
        artifact.setFile(file.toFile());
        artifacts.add(artifact);
        return artifact;
    }

    @Test
    void copiesASnapshotWheelUnderItsWheelNameAndSetsItsVersions() throws Exception {
        Path file = wheel("example_package-1.0.0-SNAPSHOT-py3-none-any.whl", "example_package",
                "1.0.0.dev20261109092818", "py3-none-any");
        dependency("example_package", "1.0.0-SNAPSHOT", "whl", "py3-none-any", file);

        WheelsMojo mojo = mojo();
        mojo.execute();

        Path copied = output().resolve("example_package-1.0.0.dev20261109092818-py3-none-any.whl");
        assertArrayEquals(Files.readAllBytes(file), Files.readAllBytes(copied));
        Properties properties = mavenProject.getProperties();
        assertEquals("example_package-1.0.0.dev20261109092818-py3-none-any.whl",
                properties.getProperty("python.wheel.example_package.file"));
        assertEquals("1.0.0.dev20261109092818", properties.getProperty("python.wheel.example_package.version"));
        assertEquals("1.0.0~~dev20261109092818", properties.getProperty("python.wheel.example_package.rpmVersion"));
        assertEquals(properties.getProperty("python.wheel.example_package.file"),
                properties.getProperty("python.wheel.file"));
        assertEquals("1.0.0.dev20261109092818", properties.getProperty("python.wheel.version"));
        assertEquals("1.0.0~~dev20261109092818", properties.getProperty("python.wheel.rpmVersion"));
        assertEquals(6, properties.size());
        assertEquals(List.of("Copied com.example:example_package:whl:py3-none-any:1.0.0-SNAPSHOT as"
                + " example_package-1.0.0.dev20261109092818-py3-none-any.whl"), log(mojo).info);
    }

    @Test
    void keepsAFeatureBranchsLocalVersionLabel() throws Exception {
        dependency("example_package", "1.0.0-feature_login-SNAPSHOT", "whl", "py3-none-any",
                wheel("example_package-1.0.0-feature_login-SNAPSHOT-py3-none-any.whl", "example_package",
                        "1.0.0.dev20261109092818+feature.login", "py3-none-any"));

        mojo().execute();

        assertTrue(Files.isRegularFile(
                output().resolve("example_package-1.0.0.dev20261109092818+feature.login-py3-none-any.whl")));
        assertEquals("1.0.0~~dev20261109092818+feature.login",
                mavenProject.getProperties().getProperty("python.wheel.example_package.rpmVersion"));
    }

    @Test
    void aReleaseKeepsItsVersion() throws Exception {
        dependency("example_package", "1.0.0", "whl", "py3-none-any",
                wheel("example_package-1.0.0-py3-none-any.whl", "example_package", "1.0.0", "py3-none-any"));

        mojo().execute();

        assertTrue(Files.isRegularFile(output().resolve("example_package-1.0.0-py3-none-any.whl")));
        assertEquals("1.0.0", mavenProject.getProperties().getProperty("python.wheel.example_package.rpmVersion"));
    }

    @Test
    void normalizesTheNameAndCompressesSeveralTagsAsAWheelNameDoes() throws Exception {
        dependency("odd-name", "2.0", "whl", null,
                wheel("odd-name-2.0.whl", "Odd.Name--Here", "2.0", "py2-none-any", "py3-none-any"));

        mojo().execute();

        assertTrue(Files.isRegularFile(output().resolve("odd_name_here-2.0-py2.py3-none-any.whl")));
    }

    @Test
    void keepsABuildTag() throws Exception {
        Path file = wheel("pkg.whl", Map.of(
                "pkg-1.0.dist-info/METADATA", "Name: pkg\nVersion: 1.0\n",
                "pkg-1.0.dist-info/WHEEL", "Wheel-Version: 1.0\nBuild: 2\nTag: cp312-cp312-linux_x86_64\n"));
        dependency("pkg", "1.0", "whl", "cp312-cp312-linux_x86_64", file);

        mojo().execute();

        assertTrue(Files.isRegularFile(output().resolve("pkg-1.0-2-cp312-cp312-linux_x86_64.whl")));
    }

    @Test
    void leavesOtherDependenciesAlone() throws Exception {
        dependency("library", "1.0", "jar", null, Files.writeString(project.resolve("library.jar"), "jar"));
        dependency("example_package", "1.0.0", "tar.gz", null,
                Files.writeString(project.resolve("example_package-1.0.0.tar.gz"), "sdist"));

        WheelsMojo mojo = mojo();
        mojo.execute();

        assertFalse(Files.exists(output()));
        assertEquals(List.of("The project has no wheel dependencies to copy."), log(mojo).warnings);
        assertTrue(mavenProject.getProperties().isEmpty());
    }

    @Test
    void setsTheWheelNeutralPropertiesOnlyWhenThereIsOneWheel() throws Exception {
        dependency("first", "1.0", "whl", "py3-none-any", wheel("first.whl", "first", "1.0", "py3-none-any"));
        dependency("second", "2.0", "whl", "py3-none-any", wheel("second.whl", "second", "2.0", "py3-none-any"));

        mojo().execute();

        Properties properties = mavenProject.getProperties();
        assertEquals("first-1.0-py3-none-any.whl", properties.getProperty("python.wheel.first.file"));
        assertEquals("second-2.0-py3-none-any.whl", properties.getProperty("python.wheel.second.file"));
        assertFalse(properties.containsKey("python.wheel.file"));
        assertEquals(6, properties.size());
    }

    @Test
    void copiesEveryWheelOfAnArtifactButSetsNoPropertiesWhenThereAreSeveral() throws Exception {
        dependency("native", "1.0", "whl", "cp312-cp312-linux_x86_64",
                wheel("a.whl", "native", "1.0", "cp312-cp312-linux_x86_64"));
        dependency("native", "1.0", "whl", "cp313-cp313-linux_x86_64",
                wheel("b.whl", "native", "1.0", "cp313-cp313-linux_x86_64"));

        WheelsMojo mojo = mojo();
        mojo.execute();

        assertTrue(Files.isRegularFile(output().resolve("native-1.0-cp312-cp312-linux_x86_64.whl")));
        assertTrue(Files.isRegularFile(output().resolve("native-1.0-cp313-cp313-linux_x86_64.whl")));
        assertTrue(mavenProject.getProperties().isEmpty());
        assertEquals(List.of("Not setting python.wheel.native.*: the project depends on 2 wheels of native."),
                log(mojo).warnings);
    }

    @Test
    void setsNoRpmVersionForAVersionRpmCantExpress() throws Exception {
        dependency("pkg", "1.0", "whl", "py3-none-any", wheel("pkg.whl", "pkg", "1!1.0", "py3-none-any"));

        WheelsMojo mojo = mojo();
        mojo.execute();

        Properties properties = mavenProject.getProperties();
        assertEquals("pkg-1!1.0-py3-none-any.whl", properties.getProperty("python.wheel.pkg.file"));
        assertEquals("1!1.0", properties.getProperty("python.wheel.pkg.version"));
        assertFalse(properties.containsKey("python.wheel.pkg.rpmVersion"));
        assertFalse(properties.containsKey("python.wheel.rpmVersion"));
        assertEquals(List.of("Not setting python.wheel.pkg.rpmVersion: can't convert 1!1.0 to an RPM version.",
                "Not setting python.wheel.rpmVersion: can't convert 1!1.0 to an RPM version."), log(mojo).warnings);
    }

    @ParameterizedTest
    @ValueSource(strings = {"no-dist-info", "no-version", "no-tag"})
    void failsOnAFileThatIsntAWheel(String broken) throws Exception {
        Map<String, String> entries;
        switch (broken) {
            case "no-dist-info":
                entries = Map.of("pkg/__init__.py", "");
                break;
            case "no-version":
                entries = Map.of("pkg-1.0.dist-info/METADATA", "Name: pkg\n",
                        "pkg-1.0.dist-info/WHEEL", "Tag: py3-none-any\n");
                break;
            default:
                entries = Map.of("pkg-1.0.dist-info/METADATA", "Name: pkg\nVersion: 1.0\n",
                        "pkg-1.0.dist-info/WHEEL", "Wheel-Version: 1.0\n");
        }
        dependency("pkg", "1.0", "whl", "py3-none-any", wheel("pkg.whl", entries));

        MojoExecutionException e = assertThrows(MojoExecutionException.class, () -> mojo().execute());

        assertTrue(e.getMessage().startsWith("com.example:pkg:whl:py3-none-any:1.0 is not a valid wheel"),
                e.getMessage());
    }

    @Test
    void failsOnAFileThatIsntAZip() throws Exception {
        Path file = project.resolve("pkg.whl");
        try (OutputStream out = Files.newOutputStream(file)) {
            out.write("not a zip".getBytes(StandardCharsets.UTF_8));
        }
        dependency("pkg", "1.0", "whl", "py3-none-any", file);

        MojoExecutionException e = assertThrows(MojoExecutionException.class, () -> mojo().execute());

        assertTrue(e.getMessage().startsWith("Failed to read the wheel com.example:pkg:whl:py3-none-any:1.0"),
                e.getMessage());
    }

    @ParameterizedTest
    @CsvSource({
            "1.0.0, 1.0.0",
            "1.0.0.dev20261109092818, 1.0.0~~dev20261109092818",
            "1.0.0rc1, 1.0.0~rc1",
            "1.0a2, 1.0~a2",
            "1.0b1.dev3, 1.0~b1~dev3",
            "2.0.post1, 2.0^post1",
            "2.0.post1.dev2, 2.0^post1~dev2",
            "1.0.0.dev1+feature.login, 1.0.0~~dev1+feature.login",
            "1.0+Local.7, 1.0+local.7",
    })
    void convertsPep440ToRpmVersions(String pep440, String rpm) {
        assertEquals(Optional.of(rpm), WheelsMojo.rpmVersion(pep440));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1!2.0", "1.0-SNAPSHOT", "1.0.0.dev", "latest"})
    void cantConvertWhatIsntANormalizedPep440VersionWithoutAnEpoch(String version) {
        assertEquals(Optional.empty(), WheelsMojo.rpmVersion(version));
    }
}
