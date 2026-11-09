package io.github.realms42.mojos;

import static io.github.realms42.mojos.MojoTesting.configure;
import static io.github.realms42.mojos.MojoTesting.log;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.realms42.mojos.MojoTesting.Commands;

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

    @Test
    void snapshotsGetTheBuildsStartTimeInUtc() throws Exception {
        BaseMojo mojo = mojo("2.0.0-SNAPSHOT");
        mojo.buildStartTime = Date.from(Instant.parse("2026-12-31T23:59:58Z"));
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"));

            assertEquals("2.0.0.dev20261231235958", mojo.versionToPep440());
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void uvEnvironmentKeepsEverythingUvAndItsToolsWriteUnderTarget() {
        Path work = project.resolve("target").resolve("python-maven-plugin");

        assertEquals(Map.of(
                "UV_PROJECT_ENVIRONMENT", work.resolve(".venv").toString(),
                "PYTHONPYCACHEPREFIX", work.resolve("pycache").toString(),
                "RUFF_CACHE_DIR", work.resolve("ruff-cache").toString()),
                mojo("1.0.0-SNAPSHOT").uvEnvironment());
    }

    @Test
    void mavenOfflineModeKeepsUvOffline() {
        BaseMojo mojo = mojo("1.0.0");
        mojo.offline = true;

        assertEquals("1", mojo.uvEnvironment().get("UV_OFFLINE"));
    }

    @Test
    void uvStaysOnlineWhenMavenIs() {
        assertFalse(mojo("1.0.0").uvEnvironment().containsKey("UV_OFFLINE"));
    }

    @Test
    void installToolCreatesTheEnvironmentAndInstallsThePinnedVersion() throws Exception {
        Commands commands = new Commands();
        Path tools = project.resolve("target/python-maven-plugin/tools");

        Path ruff = recording(commands).installTool(tools, "ruff", "0.17.3");

        assertEquals(List.of(
                List.of("uv", "venv", tools.toString()),
                List.of("uv", "pip", "install", "--python", tools.toString(), "ruff==0.17.3")),
                commands.commands);
        assertEquals(BaseMojo.venvExecutable(tools, "ruff"), ruff);
    }

    @Test
    void installToolReusesAnExistingEnvironment() throws Exception {
        Commands commands = new Commands();
        Path tools = Files.createDirectories(project.resolve("target/python-maven-plugin/tools"));

        recording(commands).installTool(tools, "ruff", "0.17.3");

        assertEquals(List.of(List.of("uv", "pip", "install", "--python", tools.toString(), "ruff==0.17.3")),
                commands.commands);
    }

    @Test
    void installToolRunsUvInTheBuildsEnvironmentSoItStaysOfflineUnderMavenOffline() throws Exception {
        Commands commands = new Commands();
        BaseMojo mojo = recording(commands);
        mojo.offline = true;

        mojo.installTool(project.resolve("tools"), "ruff", "0.17.3");

        for (Map<String, String> env : commands.environments) {
            assertEquals("1", env.get("UV_OFFLINE"));
        }
    }

    @Test
    void thePluginsEnvironmentsAreNamedSoAProjectsOwnRuffSkipsThem() {
        BaseMojo mojo = mojo("1.0.0");

        assertEquals(".venv", mojo.getProjectVenvPath().getFileName().toString());
        assertEquals(".venv", mojo.getToolsVenvPath().getFileName().toString());
    }

    @Test
    void theProjectsEnvironmentIsThePluginsOwnUnderTargetNeverTheProjectsVenv() {
        assertEquals(project.resolve("target/python-maven-plugin/.venv").toString(),
                mojo("1.0.0").uvEnvironment().get("UV_PROJECT_ENVIRONMENT"));
    }

    private BaseMojo recording(Commands commands) {
        return configure(new BaseMojo() {
            @Override
            public void execute() {
            }
        }, project, "1.0.0", commands);
    }

    @Test
    void exitCodeOneFromACheckFailsTheBuild() {
        MojoFailureException e = assertThrows(MojoFailureException.class,
                () -> mojo("1.0.0").checkExitCode("ruff check", "ruff found lint violations.", 1));

        assertEquals("ruff found lint violations.", e.getMessage());
    }

    @Test
    void otherExitCodesFromACheckAreBuildErrorsNamingTheTool() {
        MojoExecutionException e = assertThrows(MojoExecutionException.class,
                () -> mojo("1.0.0").checkExitCode("pytest", "There are test failures.", 4));

        assertEquals("`pytest` failed with exit code 4. See the output above.", e.getMessage());
    }

    @Test
    void exitCodeZeroFromACheckPasses() throws Exception {
        mojo("1.0.0").checkExitCode("pytest", "There are test failures.", 0);
    }

    @Test
    void ruffExcludesTheBuildDirectoryByItsAbsolutePath() {
        assertEquals("extend-exclude = [\"" + project.resolve("target") + "\"]",
                mojo("1.0.0").ruffExcludeBuildDir());
    }

    @Test
    void ruffExcludeEscapesThePathForToml() {
        BaseMojo mojo = mojo("1.0.0");
        mojo.buildDir = new File("/builds/it's \"quoted\"\\target");

        assertEquals("extend-exclude = [\"/builds/it's \\\"quoted\\\"\\\\target\"]", mojo.ruffExcludeBuildDir());
    }

    @Test
    void execRunsInTheProjectDirectory() throws Exception {
        BaseMojo mojo = mojo("1.0.0");

        assertEquals(0, mojo.exec("sh", List.of("-c", "pwd"), Map.of()));

        assertTrue(log(mojo).info.contains(project.toString()), () -> "output: " + log(mojo).info);
    }

    @Test
    void execLogsTheToolsOutputAtInfoAndTheCommandLineAtDebug() throws Exception {
        BaseMojo mojo = mojo("1.0.0");

        mojo.exec("sh", List.of("-c", "echo out; echo err >&2"), Map.of());

        assertEquals(Set.of("out", "err"), Set.copyOf(log(mojo).info));
        assertEquals(2, log(mojo).info.size());
        assertEquals(1, log(mojo).debug.size());
        assertTrue(log(mojo).debug.get(0).startsWith("Running command: "), () -> "debug: " + log(mojo).debug);
    }

    @Test
    void execPassesTheEnvironmentAndReturnsTheExitCode() throws Exception {
        BaseMojo mojo = mojo("1.0.0");

        int exitCode = mojo.exec("sh", List.of("-c", "echo \"$GREETING\"; exit 3"), Map.of("GREETING", "hello"));

        assertEquals(3, exitCode);
        assertTrue(log(mojo).info.contains("hello"), () -> "output: " + log(mojo).info);
    }

    @Test
    void runFailsOnANonZeroExitCode() {
        MojoExecutionException e = assertThrows(MojoExecutionException.class,
                () -> mojo("1.0.0").run("sh", List.of("-c", "exit 3"), Map.of()));

        assertEquals("`sh -c` failed with exit code 3. See the output above.", e.getMessage());
    }
}
