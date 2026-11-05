package com.github.realms42.mojos;

import static com.github.realms42.mojos.MojoTesting.configure;
import static com.github.realms42.mojos.MojoTesting.log;
import static com.github.realms42.mojos.MojoTesting.set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.github.realms42.mojos.MojoTesting.Commands;

class TestMojoTest {

    @TempDir
    Path project;

    private final Commands commands = new Commands();

    private Path venv() {
        return project.resolve("target/python-maven-plugin/.venv");
    }

    private TestMojo mojo() {
        TestMojo mojo = configure(new TestMojo() {
            @Override
            protected int exec(String cmd, List<String> args, Map<String, String> env) {
                int exitCode = commands.exec(cmd, args, env);
                // Like the real `uv sync`, which creates the environment the pinned pytest is installed into.
                if (exitCode == 0 && args.equals(List.of("sync", "--locked"))) {
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
        return mojo;
    }

    @Test
    void runsThePluginsPinnedPytestInTheProjectsEnvironmentWithACacheUnderTargetAndAJUnitReport()
            throws Exception {
        mojo().execute();

        Path work = project.resolve("target/python-maven-plugin");
        assertEquals(List.of(
                List.of("uv", "sync", "--locked"),
                List.of("uv", "pip", "install", "--python", venv().toString(), "pytest==9.2.0"),
                List.of(BaseMojo.venvExecutable(venv(), "pytest").toString(),
                        "-o", "cache_dir=" + work.resolve("pytest-cache"),
                        "--ignore=" + project.resolve("target"),
                        "--junitxml=" + project.resolve("target/surefire-reports/TEST-pytest.xml"))),
                commands.commands);
        for (Map<String, String> env : commands.environments) {
            assertEquals("1.2.0.dev0", env.get("PACKAGE_VERSION"));
            assertEquals(venv().toString(), env.get("UV_PROJECT_ENVIRONMENT"));
        }
    }

    @Test
    void pytestVersionChoosesTheVersionInstalled() throws Exception {
        TestMojo mojo = mojo();
        set(mojo, "pytestVersion", "8.4.0");

        mojo.execute();

        assertEquals("pytest==8.4.0", commands.commands.get(1).get(5));
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
        commands.exitingWith(0, 0, 5);
        TestMojo mojo = mojo();

        mojo.execute();

        assertEquals(List.of("No tests to run."), log(mojo).info);
    }

    @Test
    void failsTheBuildWhenTestsFail() {
        commands.exitingWith(0, 0, 1);

        MojoFailureException e = assertThrows(MojoFailureException.class, () -> mojo().execute());

        assertEquals("There are test failures. See pytest's output above.", e.getMessage());
    }

    @Test
    void reportsABuildErrorWhenPytestCantRun() {
        commands.exitingWith(0, 0, 2);

        MojoExecutionException e = assertThrows(MojoExecutionException.class, () -> mojo().execute());

        assertEquals("`pytest` failed with exit code 2. See the output above.", e.getMessage());
    }

    // uv also exits with 1, for example when the project fails to build, so the environment is set up in its own
    // steps and a failure there is never reported as test failures.
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
}
