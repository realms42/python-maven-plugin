package com.github.realms42.mojos;

import static com.github.realms42.mojos.MojoTesting.configure;
import static com.github.realms42.mojos.MojoTesting.log;
import static com.github.realms42.mojos.MojoTesting.set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
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
            // The version variables apply to every package a backend builds, including dependencies built from
            // source during `uv sync`, so only `uv build` gets them.
            assertFalse(env.containsKey("SETUPTOOLS_SCM_PRETEND_VERSION"), env.toString());
            assertFalse(env.containsKey("PDM_BUILD_SCM_VERSION"), env.toString());
            assertEquals(venv().toString(), env.get("UV_PROJECT_ENVIRONMENT"));
        }
    }

    // A parent POM's pytest settings are -o overrides, applied over the project's own configuration, in a stable
    // order. The plugin's own options come last, so the settings can't move the cache or the report.
    @Test
    void appliesThePytestSettingsBeforeThePluginsOwnOptions() throws Exception {
        TestMojo mojo = mojo();
        Map<String, String> settings = new HashMap<>();
        settings.put("xfail_strict", " true ");
        settings.put("addopts", "--strict-markers -p no:randomly");
        settings.put("cache_dir", "/elsewhere");
        set(mojo, "pytestSettings", settings);

        mojo.execute();

        List<String> command = commands.commands.get(2);
        assertEquals(List.of("-o", "addopts=--strict-markers -p no:randomly", "-o", "cache_dir=/elsewhere",
                "-o", "xfail_strict=true",
                "-o", "cache_dir=" + project.resolve("target/python-maven-plugin/pytest-cache")),
                command.subList(1, 9));
    }

    // `-o addopts=` clears an option, so an entry without a value is passed on as empty.
    @Test
    void passesAnEmptySettingOnAsAnEmptyValue() throws Exception {
        TestMojo mojo = mojo();
        Map<String, String> settings = new HashMap<>();
        settings.put("addopts", null);
        set(mojo, "pytestSettings", settings);

        mojo.execute();

        assertEquals(List.of("-o", "addopts="), commands.commands.get(2).subList(1, 3));
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
