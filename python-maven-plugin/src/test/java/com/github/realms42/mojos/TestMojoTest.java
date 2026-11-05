package com.github.realms42.mojos;

import static com.github.realms42.mojos.MojoTesting.configure;
import static com.github.realms42.mojos.MojoTesting.log;
import static com.github.realms42.mojos.MojoTesting.set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    private TestMojo mojo() {
        return configure(new TestMojo() {
            @Override
            protected int exec(String cmd, List<String> args, Map<String, String> env) {
                return commands.exec(cmd, args, env);
            }
        }, project, "1.2.0-SNAPSHOT");
    }

    @Test
    void runsPytestThroughUvWithItsCacheUnderTargetTargetIgnoredAndAJUnitReport() throws Exception {
        mojo().execute();

        Path work = project.resolve("target/python-maven-plugin");
        assertEquals(List.of("uv", "sync", "--locked"), commands.commands.get(0));
        assertEquals(
                List.of("uv", "run", "--no-sync", "--with", "pytest", "pytest",
                        "-o", "cache_dir=" + work.resolve("pytest-cache"),
                        "--ignore=" + project.resolve("target"),
                        "--junitxml=" + project.resolve("target/surefire-reports/TEST-pytest.xml")),
                commands.commands.get(1));
        for (Map<String, String> env : commands.environments) {
            assertEquals("1.2.0.dev0", env.get("PACKAGE_VERSION"));
            assertEquals(work.resolve(".venv").toString(), env.get("UV_PROJECT_ENVIRONMENT"));
        }
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
        commands.exitingWith(0, 5);
        TestMojo mojo = mojo();

        mojo.execute();

        assertEquals(List.of("No tests to run."), log(mojo).info);
    }

    @Test
    void failsTheBuildWhenTheProjectFailsTheCheck() {
        commands.exitingWith(0, 1);

        MojoFailureException e = assertThrows(MojoFailureException.class, () -> mojo().execute());

        assertEquals("There are test failures. See pytest's output above.", e.getMessage());
    }

    @Test
    void reportsABuildErrorWhenTheToolCantRun() {
        commands.exitingWith(0, 2);

        MojoExecutionException e = assertThrows(MojoExecutionException.class, () -> mojo().execute());

        assertEquals("`uv run pytest` failed with exit code 2. See the output above.", e.getMessage());
    }

    // uv also exits with 1, for example when the project fails to build, so the environment is set up in its
    // own step and a failure there is never reported as the check failing.
    @Test
    void reportsABuildErrorWithoutRunningTheToolWhenUvCantSetUpTheProject() {
        commands.exitingWith(1);

        MojoExecutionException e = assertThrows(MojoExecutionException.class, () -> mojo().execute());

        assertEquals("`uv sync` failed with exit code 1. See the output above.", e.getMessage());
        assertEquals(1, commands.commands.size());
    }
}
