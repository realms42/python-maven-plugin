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
    void runsPytestThroughUvWithItsCacheUnderTarget() throws Exception {
        mojo().execute();

        Path work = project.resolve("target/python-maven-plugin");
        assertEquals(
                List.of("uv", "run", "--locked", "--with", "pytest", "pytest",
                        "-o", "cache_dir=" + work.resolve("pytest-cache")),
                commands.commands.get(0));
        assertEquals("1.2.0.dev0", commands.environments.get(0).get("PACKAGE_VERSION"));
        assertEquals(work.resolve(".venv").toString(), commands.environments.get(0).get("UV_PROJECT_ENVIRONMENT"));
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
        commands.exitingWith(5);
        TestMojo mojo = mojo();

        mojo.execute();

        assertEquals(List.of("No tests to run."), log(mojo).info);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void failsOnTestFailuresAndOnUvErrors(int exitCode) {
        commands.exitingWith(exitCode);

        MojoExecutionException e = assertThrows(MojoExecutionException.class, () -> mojo().execute());

        assertEquals("Command failed with exit code " + exitCode, e.getMessage());
    }
}
