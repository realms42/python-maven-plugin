package io.github.realms42.mojos;

import static io.github.realms42.mojos.MojoTesting.configure;
import static io.github.realms42.mojos.MojoTesting.log;
import static io.github.realms42.mojos.MojoTesting.set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.realms42.mojos.MojoTesting.Commands;

class LintMojoTest {

    @TempDir
    Path project;

    private final Commands commands = new Commands();

    private LintMojo mojo() {
        LintMojo mojo = configure(new LintMojo() {
            @Override
            protected int exec(String cmd, List<String> args, Map<String, String> env) {
                return commands.exec(cmd, args, env);
            }
        }, project, "1.0.0");
        set(mojo, "ruffVersion", "0.17.3");
        set(mojo, "enforcedRules", List.of("E", "W", "F", "I", "B", "UP"));
        return mojo;
    }

    private Path tools() {
        return project.resolve("target/python-maven-plugin/tools/.venv");
    }

    @Test
    void runsThePluginsPinnedRuffFromItsOwnEnvironmentWithoutFixingAndOutsideTheBuildDirectory() throws Exception {
        mojo().execute();

        assertEquals(List.of(
                List.of("uv", "venv", tools().toString()),
                List.of("uv", "pip", "install", "--python", tools().toString(), "ruff==0.17.3"),
                List.of(BaseMojo.venvExecutable(tools(), "ruff").toString(), "check",
                        "--config", "extend-exclude = [\"" + project.resolve("target") + "\"]",
                        "--extend-select", "E,W,F,I,B,UP")),
                commands.commands);
        assertEquals(project.resolve("target/python-maven-plugin/ruff-cache").toString(),
                commands.environments.get(2).get("RUFF_CACHE_DIR"));
    }

    @Test
    void neverSetsUpTheProjectsEnvironment() throws Exception {
        mojo().execute();

        for (List<String> command : commands.commands) {
            assertFalse(command.contains("sync"), command.toString());
        }
    }

    @Test
    void ruffVersionChoosesTheVersionInstalled() throws Exception {
        LintMojo mojo = mojo();
        set(mojo, "ruffVersion", "0.15.0");

        mojo.execute();

        assertEquals("ruff==0.15.0", commands.commands.get(1).get(5));
    }

    @Test
    void appliesTheRuffSettingsOverTheProjectsOwnConfiguration() throws Exception {
        LintMojo mojo = mojo();
        Map<String, String> settings = new HashMap<>();
        settings.put("target-version", "\"py311\"");
        settings.put("line-length", " 100 ");
        settings.put("lint.pydocstyle.convention", "\"google\"");
        set(mojo, "ruffSettings", settings);

        mojo.execute();

        assertEquals(List.of(BaseMojo.venvExecutable(tools(), "ruff").toString(), "check",
                "--config", "extend-exclude = [\"" + project.resolve("target") + "\"]",
                "--config", "line-length = 100",
                "--config", "lint.pydocstyle.convention = \"google\"",
                "--config", "target-version = \"py311\"",
                "--extend-select", "E,W,F,I,B,UP"), commands.commands.get(2));
    }

    @Test
    void rejectsARuffSettingWithoutAValueBeforeRunningAnything() {
        LintMojo mojo = mojo();
        Map<String, String> settings = new HashMap<>();
        settings.put("line-length", null);
        set(mojo, "ruffSettings", settings);

        MojoExecutionException e = assertThrows(MojoExecutionException.class, mojo::execute);

        assertTrue(e.getMessage().startsWith("The ruffSettings entry <line-length> has no value."), e.getMessage());
        assertTrue(commands.commands.isEmpty());
    }

    @Test
    void enforcesTheConfiguredRulesOnTopOfTheProjectsOwnConfiguration() throws Exception {
        LintMojo mojo = mojo();
        set(mojo, "enforcedRules", List.of("E", " F ", "S", ""));

        mojo.execute();

        List<String> command = commands.commands.get(2);
        assertEquals(List.of("--extend-select", "E,F,S"), command.subList(command.size() - 2, command.size()));
    }

    @Test
    void lintsWithTheProjectsOwnConfigurationAloneWhenNoRulesAreEnforced() throws Exception {
        for (List<String> none : Arrays.asList(List.<String>of(), List.of(" "), null)) {
            LintMojo mojo = mojo();
            set(mojo, "enforcedRules", none);

            mojo.execute();
        }

        for (List<String> command : commands.commands) {
            assertFalse(command.contains("--extend-select"), command.toString());
        }
        assertEquals(9, commands.commands.size());
    }

    @Test
    void skipFlagSkipsTheCheck() throws Exception {
        LintMojo mojo = mojo();
        set(mojo, "skip", true);

        mojo.execute();

        assertTrue(commands.commands.isEmpty());
        assertEquals(List.of("Linting is skipped."), log(mojo).info);
    }

    @Test
    void failsTheBuildWhenRuffFindsViolations() {
        commands.exitingWith(0, 0, 1);

        MojoFailureException e = assertThrows(MojoFailureException.class, () -> mojo().execute());

        assertEquals("ruff found lint violations. See its output above.", e.getMessage());
    }

    @Test
    void reportsABuildErrorWhenRuffCantRun() {
        commands.exitingWith(0, 0, 2);

        MojoExecutionException e = assertThrows(MojoExecutionException.class, () -> mojo().execute());

        assertEquals("`ruff check` failed with exit code 2. See the output above.", e.getMessage());
    }

    @Test
    void reportsABuildErrorWithoutLintingWhenRuffCantBeInstalled() {
        commands.exitingWith(0, 1);

        MojoExecutionException e = assertThrows(MojoExecutionException.class, () -> mojo().execute());

        assertEquals("`uv pip` failed with exit code 1. See the output above.", e.getMessage());
        assertEquals(2, commands.commands.size());
    }
}
