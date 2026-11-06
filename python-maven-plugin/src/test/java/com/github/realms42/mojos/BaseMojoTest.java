package com.github.realms42.mojos;

import static com.github.realms42.mojos.MojoTesting.configure;
import static com.github.realms42.mojos.MojoTesting.log;
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
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.github.realms42.mojos.MojoTesting.Commands;

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
            "1.0.0-SNAPSHOT, 1.0.0.dev20261105152300",
            "1.0.0-alpha-1, 1.0.0a1",
            "1.0.0-alpha2, 1.0.0a2",
            "1.0.0-beta-3, 1.0.0b3",
            "1.0.0-rc-1, 1.0.0rc1",
            "2.0-RC2, 2.0rc2",
            "1.0.0.RELEASE, 1.0.0",
            "1.0.0.Final, 1.0.0",
            "1.0.0-GA, 1.0.0",
            "1.0.0-beta, 1.0.0b0",
            "1.0.0-cr-2, 1.0.0rc2",
            "1.0.0-a1, 1.0.0a1",
            "1.0.0-RC1-SNAPSHOT, 1.0.0rc1.dev20261105152300",
            "1.0.0-snapshot, 1.0.0.dev20261105152300",
            // gitflow-maven-plugin: hotfix and feature branch versions
            "1.0.2.1-SNAPSHOT, 1.0.2.1.dev20261105152300",
            "1.0.0-feature_name-SNAPSHOT, 1.0.0.dev20261105152300+feature.name",
            "1.0.0-feature/JIRA-42_Add-Login-SNAPSHOT, 1.0.0.dev20261105152300+feature.jira.42.add.login",
            // git-branch-as-version, jgitver-style branch and distance qualifiers
            "2.1.4-TR-123-SNAPSHOT, 2.1.4.dev20261105152300+tr.123",
            "1.0.1-3-SNAPSHOT, 1.0.1.dev20261105152300+3",
            "1.0.0-my-feature, 1.0.0+my.feature",
            // Versions the tools really produced, building feature branches of example_package:
            // gitflow-maven-plugin 1.21.0 feature-start (the feature name, without feature/)...
            "1.0.0-login-SNAPSHOT, 1.0.0.dev20261105152300+login",
            "1.0.0-JIRA-42_Add-Login-SNAPSHOT, 1.0.0.dev20261105152300+jira.42.add.login",
            "1.0.0-Release-Candidate-Fixes-SNAPSHOT, 1.0.0.dev20261105152300+release.candidate.fixes",
            "1.0.0-user.profile_v2-SNAPSHOT, 1.0.0.dev20261105152300+user.profile.v2",
            // ...and jgitver 1.9.0 (the whole branch name, lowercased).
            "1.0.1-feature_jira_42_add_login-SNAPSHOT, 1.0.1.dev20261105152300+feature.jira.42.add.login",
            "1.0.1-bugfix_issue_7-SNAPSHOT, 1.0.1.dev20261105152300+bugfix.issue.7",
            // Branch names that start like a pre-release are still branch names.
            "1.0.0-a-team-SNAPSHOT, 1.0.0.dev20261105152300+a.team",
            "1.0.0-rc-fixes-SNAPSHOT, 1.0.0.dev20261105152300+rc.fixes",
            "1.0.0-beta_feature, 1.0.0+beta.feature",
            "1.0.0-b-SNAPSHOT, 1.0.0.dev20261105152300+b",
    })
    void convertsTheMavenVersionToPep440(String maven, String pep440) throws Exception {
        assertEquals(pep440, mojo(maven).versionToPep440());
    }

    // A snapshot's dev number is the build's start time, so every snapshot build gets its own version: package
    // indexes refuse a second upload of the same version. It is UTC, whatever the machine's time zone.
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
    void releasesDontDependOnTheBuildTime() throws Exception {
        BaseMojo mojo = mojo("2.0.0");
        mojo.buildStartTime = Date.from(Instant.parse("2030-01-01T00:00:00Z"));

        assertEquals("2.0.0", mojo.versionToPep440());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "1.0.0-M1 | Python has no milestone releases",
            "1.0.0-milestone-2-SNAPSHOT | Python has no milestone releases",
            "latest | it must start with a numeric release",
            "SNAPSHOT | it must start with a numeric release",
            "v1.0.0 | it must start with a numeric release",
    })
    void rejectsVersionsWithNoPep440Equivalent(String maven, String reason) {
        MojoExecutionException e = assertThrows(MojoExecutionException.class, () -> mojo(maven).versionToPep440());

        assertTrue(e.getMessage().startsWith("Can't convert the Maven version " + maven + " "), e.getMessage());
        assertTrue(e.getMessage().contains(reason), e.getMessage());
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

    // `uv pip install` replaces a version already there, so an existing environment is reused, not recreated.
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

    // ruff skips directories named .venv by default, so a project running its own ruff doesn't lint the plugin's
    // environments even when target/ isn't in its .gitignore.
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

            @Override
            protected int exec(String cmd, List<String> args, Map<String, String> env) {
                return commands.exec(cmd, args, env);
            }
        }, project, "1.0.0");
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

        assertEquals("`sh -c` failed with exit code 3. See the output above.", e.getMessage());
    }
}
