package com.github.realms42.mojos;

import static com.github.realms42.mojos.MojoTesting.configure;
import static com.github.realms42.mojos.MojoTesting.log;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BaseMojoTest {

    @TempDir
    Path project;

    private BaseMojo mojo(String version) {
        return configure(new BaseMojo() {
            @Override
            public void execute() {
            }
        }, project, version);
    }

    @ParameterizedTest
    @CsvSource({
            "1.0.0, 1.0.0",
            "1.0.0-SNAPSHOT, 1.0.0.dev0",
            "1.0.0-alpha-1, 1.0.0a1",
            "1.0.0-alpha2, 1.0.0a2",
            "1.0.0-beta-3, 1.0.0b3",
            "1.0.0-rc-1, 1.0.0rc1",
            "2.0-RC2, 2.0rc2",
            "1.0.0.RELEASE, 1.0.0",
            "1.0.0.Final, 1.0.0",
            "1.0.0-GA, 1.0.0",
    })
    void convertsTheMavenVersionToPep440(String maven, String pep440) {
        assertEquals(pep440, mojo(maven).versionToPep440());
    }

    @Test
    void uvEnvironmentKeepsEverythingUvAndItsToolsWriteUnderTarget() {
        Path work = project.resolve("target").resolve("python-maven-plugin");

        assertEquals(Map.of(
                "PACKAGE_VERSION", "1.0.0.dev0",
                "UV_PROJECT_ENVIRONMENT", work.resolve(".venv").toString(),
                "PYTHONPYCACHEPREFIX", work.resolve("pycache").toString(),
                "RUFF_CACHE_DIR", work.resolve("ruff-cache").toString()),
                mojo("1.0.0-SNAPSHOT").uvEnvironment());
    }

    @Test
    void uvRunIsLockedAndLetsTheProjectPinTheTool() {
        assertEquals(List.of("run", "--locked", "--with", "ruff", "ruff", "format", "--check"),
                mojo("1.0.0").uvRun("ruff", "format", "--check"));
    }

    @Test
    void ruffExcludesTheBuildDirectoryByItsAbsolutePath() {
        assertEquals("extend-exclude = [\"" + project.resolve("target") + "\"]",
                mojo("1.0.0").ruffExcludeBuildDir());
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void ruffExcludeEscapesThePathForToml() {
        BaseMojo mojo = mojo("1.0.0");
        mojo.buildDir = new File("/builds/it's \"quoted\"\\target");

        assertEquals("extend-exclude = [\"/builds/it's \\\"quoted\\\"\\\\target\"]", mojo.ruffExcludeBuildDir());
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void execRunsInTheProjectDirectory() throws Exception {
        BaseMojo mojo = mojo("1.0.0");

        assertEquals(0, mojo.exec("sh", List.of("-c", "pwd"), Map.of()));

        assertTrue(log(mojo).info.contains(project.toString()), () -> "output: " + log(mojo).info);
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void execLogsTheToolsOutputAtInfoAndTheCommandLineAtDebug() throws Exception {
        BaseMojo mojo = mojo("1.0.0");

        mojo.exec("sh", List.of("-c", "echo out; echo err >&2"), Map.of());

        assertEquals(Set.of("out", "err"), Set.copyOf(log(mojo).info));
        assertEquals(2, log(mojo).info.size());
        assertEquals(1, log(mojo).debug.size());
        assertTrue(log(mojo).debug.get(0).startsWith("Running command: "), () -> "debug: " + log(mojo).debug);
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void execPassesTheEnvironmentAndReturnsTheExitCode() throws Exception {
        BaseMojo mojo = mojo("1.0.0");

        int exitCode = mojo.exec("sh", List.of("-c", "echo \"$GREETING\"; exit 3"), Map.of("GREETING", "hello"));

        assertEquals(3, exitCode);
        assertTrue(log(mojo).info.contains("hello"), () -> "output: " + log(mojo).info);
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void runFailsOnANonZeroExitCode() {
        MojoExecutionException e = assertThrows(MojoExecutionException.class,
                () -> mojo("1.0.0").run("sh", List.of("-c", "exit 3"), Map.of()));

        assertEquals("Command failed with exit code 3", e.getMessage());
    }
}
