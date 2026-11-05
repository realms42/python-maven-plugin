package com.github.realms42.mojos;

import static com.github.realms42.mojos.MojoTesting.configure;
import static com.github.realms42.mojos.MojoTesting.log;
import static com.github.realms42.mojos.MojoTesting.set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.github.realms42.mojos.MojoTesting.Commands;

class LintMojoTest {

    @TempDir
    Path project;

    private final Commands commands = new Commands();

    private LintMojo mojo() {
        return configure(new LintMojo() {
            @Override
            protected int exec(String cmd, List<String> args, Map<String, String> env) {
                return commands.exec(cmd, args, env);
            }
        }, project, "1.0.0");
    }

    @Test
    void runsRuffCheckWithoutFixingAndOutsideTheBuildDirectory() throws Exception {
        LintMojo mojo = mojo();
        set(mojo, "enforcedRules", List.of("E", "W", "F", "I", "B", "UP"));

        mojo.execute();

        assertEquals(List.of("uv", "run", "--locked", "--with", "ruff", "ruff", "check",
                "--config", "extend-exclude = [\"" + project.resolve("target") + "\"]",
                "--extend-select", "E,W,F,I,B,UP"), commands.commands.get(0));
        assertEquals(project.resolve("target/python-maven-plugin/ruff-cache").toString(),
                commands.environments.get(0).get("RUFF_CACHE_DIR"));
    }

    // --extend-select takes precedence over a project's `ignore`, which is what keeps these rules on.
    @Test
    void enforcesTheConfiguredRulesOnTopOfTheProjectsOwnConfiguration() throws Exception {
        LintMojo mojo = mojo();
        set(mojo, "enforcedRules", List.of("E", " F ", "S", ""));

        mojo.execute();

        List<String> command = commands.commands.get(0);
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
        assertEquals(3, commands.commands.size());
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
    void failsOnLintErrors() {
        commands.exitingWith(1);

        MojoExecutionException e = assertThrows(MojoExecutionException.class, () -> mojo().execute());

        assertEquals("Command failed with exit code 1", e.getMessage());
    }
}
