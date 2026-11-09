package io.github.realms42.mojos;

import static io.github.realms42.mojos.MojoTesting.configure;
import static io.github.realms42.mojos.MojoTesting.log;
import static io.github.realms42.mojos.MojoTesting.set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.realms42.mojos.MojoTesting.Commands;

class TestMojoTest {

    @TempDir
    Path project;

    private final Commands commands = new Commands();
    private Path wheel;

    @BeforeEach
    void buildAWheel() throws IOException {
        Path output = Files.createDirectories(project.resolve("target/python-build"));
        wheel = Files.writeString(output.resolve("example-1.2.0.dev1-py3-none-any.whl"), "wheel");
        Files.writeString(output.resolve("example-1.2.0.dev1.tar.gz"), "sdist");
        Files.writeString(output.resolve(".gitignore"), "*");
    }

    private Path venv() {
        return project.resolve("target/python-maven-plugin/.venv");
    }

    private List<String> pytestCommand(String... options) {
        Path work = project.resolve("target/python-maven-plugin");
        List<String> command = new ArrayList<>();
        command.add(BaseMojo.venvExecutable(venv(), "pytest").toString());
        command.addAll(List.of(options));
        command.addAll(List.of(
                "-o", "cache_dir=" + work.resolve("pytest-cache"),
                "--ignore=" + project.resolve("target"),
                "--junitxml=" + project.resolve("target/surefire-reports/TEST-pytest.xml")));
        return command;
    }

    private TestMojo mojo() {
        TestMojo mojo = configure(new TestMojo() {
            @Override
            protected int exec(String cmd, List<String> args, Map<String, String> env) {
                int exitCode = commands.exec(cmd, args, env);
                if (exitCode == 0 && args.get(0).equals("sync")) {
                    try {
                        Files.createDirectories(venv());
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
                return exitCode;
            }
        }, project, "1.2.0-SNAPSHOT");
        set(mojo, "pytestVersion", "9.2.0");
        set(mojo, "testWheel", true);
        return mojo;
    }

    @Test
    void testsTheBuiltWheelWithThePluginsPinnedPytestACacheUnderTargetAndAJUnitReport() throws Exception {
        TestMojo mojo = mojo();

        mojo.execute();

        assertEquals(List.of(
                List.of("uv", "sync", "--locked", "--no-install-project"),
                List.of("uv", "pip", "install", "--python", venv().toString(), "pytest==9.2.0"),
                List.of("uv", "pip", "install", "--python", venv().toString(), "--no-deps", "--reinstall",
                        wheel.toString()),
                pytestCommand()),
                commands.commands);
        assertTrue(log(mojo).info.contains("Testing the built wheel: example-1.2.0.dev1-py3-none-any.whl"),
                () -> "info: " + log(mojo).info);
        for (Map<String, String> env : commands.environments) {
            assertFalse(env.containsKey("SETUPTOOLS_SCM_PRETEND_VERSION"), env.toString());
            assertFalse(env.containsKey("PDM_BUILD_SCM_VERSION"), env.toString());
            assertEquals(venv().toString(), env.get("UV_PROJECT_ENVIRONMENT"));
        }
    }

    @Test
    void testsTheSourceTreeAsAnEditableInstallWhenAsked() throws Exception {
        TestMojo mojo = mojo();
        set(mojo, "testWheel", false);

        mojo.execute();

        assertEquals(List.of(
                List.of("uv", "sync", "--locked"),
                List.of("uv", "pip", "install", "--python", venv().toString(), "pytest==9.2.0"),
                pytestCommand()),
                commands.commands);
        assertTrue(log(mojo).info.contains("Testing the source tree."), () -> "info: " + log(mojo).info);
    }

    @Test
    void failsBeforeRunningAnythingWhenThereIsNoWheelToTest() throws Exception {
        Files.delete(wheel);

        MojoExecutionException e = assertThrows(MojoExecutionException.class, () -> mojo().execute());

        assertTrue(e.getMessage().startsWith("Expected exactly one wheel in "
                + project.resolve("target/python-build") + " to test, found 0."), e.getMessage());
        assertTrue(e.getMessage().contains("-Dpython.test.wheel=false"), e.getMessage());
        assertTrue(commands.commands.isEmpty());
    }

    @Test
    void failsWhenItCantTellWhichOfSeveralWheelsToTest() throws Exception {
        Files.writeString(wheel.resolveSibling("example-1.2.0.dev1-cp312-cp312-linux_x86_64.whl"), "wheel");

        MojoExecutionException e = assertThrows(MojoExecutionException.class, () -> mojo().execute());

        assertTrue(e.getMessage().contains("found 2."), e.getMessage());
        assertTrue(commands.commands.isEmpty());
    }

    @Test
    void pytestVersionChoosesTheVersionInstalled() throws Exception {
        TestMojo mojo = mojo();
        set(mojo, "pytestVersion", "8.4.0");

        mojo.execute();

        assertEquals("pytest==8.4.0", commands.commands.get(1).get(5));
    }

    @Test
    void appliesThePytestSettingsBeforeThePluginsOwnOptions() throws Exception {
        TestMojo mojo = mojo();
        Map<String, String> settings = new HashMap<>();
        settings.put("xfail_strict", " true ");
        settings.put("addopts", "--strict-markers -p no:randomly");
        settings.put("cache_dir", "/elsewhere");
        set(mojo, "pytestSettings", settings);

        mojo.execute();

        assertEquals(pytestCommand("-o", "addopts=--strict-markers -p no:randomly", "-o", "cache_dir=/elsewhere",
                "-o", "xfail_strict=true"), commands.commands.get(3));
    }

    @Test
    void passesAnEmptySettingOnAsAnEmptyValue() throws Exception {
        TestMojo mojo = mojo();
        Map<String, String> settings = new HashMap<>();
        settings.put("addopts", null);
        set(mojo, "pytestSettings", settings);

        mojo.execute();

        assertEquals(pytestCommand("-o", "addopts="), commands.commands.get(3));
    }

    @ParameterizedTest
    @ValueSource(strings = {"skipTests", "skip"})
    void skipTestsAndMavenTestSkipSkipTheRun(String flag) throws Exception {
        TestMojo mojo = mojo();
        set(mojo, flag, true);

        mojo.execute();

        assertTrue(commands.commands.isEmpty());
        assertEquals(List.of("Tests are skipped."), log(mojo).info);
    }

    @Test
    void passesWhenPytestCollectsNoTests() throws Exception {
        commands.exitingWith(0, 0, 0, 5);
        TestMojo mojo = mojo();

        mojo.execute();

        assertTrue(log(mojo).info.contains("No tests to run."), () -> "info: " + log(mojo).info);
    }

    @Test
    void failsTheBuildWhenTestsFail() {
        commands.exitingWith(0, 0, 0, 1);

        MojoFailureException e = assertThrows(MojoFailureException.class, () -> mojo().execute());

        assertEquals("There are test failures. See pytest's output above.", e.getMessage());
    }

    @Test
    void reportsABuildErrorWhenPytestCantRun() {
        commands.exitingWith(0, 0, 0, 2);

        MojoExecutionException e = assertThrows(MojoExecutionException.class, () -> mojo().execute());

        assertEquals("`pytest` failed with exit code 2. See the output above.", e.getMessage());
    }

    @Test
    void reportsABuildErrorWithoutTestingWhenUvCantSetUpTheProject() {
        commands.exitingWith(1);

        MojoExecutionException e = assertThrows(MojoExecutionException.class, () -> mojo().execute());

        assertEquals("`uv sync` failed with exit code 1. See the output above.", e.getMessage());
        assertEquals(1, commands.commands.size());
    }

    @Test
    void reportsABuildErrorWithoutTestingWhenPytestCantBeInstalled() {
        commands.exitingWith(0, 1);

        MojoExecutionException e = assertThrows(MojoExecutionException.class, () -> mojo().execute());

        assertEquals("`uv pip` failed with exit code 1. See the output above.", e.getMessage());
        assertEquals(2, commands.commands.size());
    }

    @Test
    void reportsABuildErrorWithoutTestingWhenTheWheelCantBeInstalled() {
        commands.exitingWith(0, 0, 1);

        MojoExecutionException e = assertThrows(MojoExecutionException.class, () -> mojo().execute());

        assertEquals("`uv pip` failed with exit code 1. See the output above.", e.getMessage());
        assertEquals(3, commands.commands.size());
    }
}
