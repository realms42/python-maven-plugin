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

import com.github.realms42.mojos.MojoTesting.Commands;

class FormatMojoTest {

    @TempDir
    Path project;

    private final Commands commands = new Commands();

    private FormatMojo mojo() {
        return configure(new FormatMojo() {
            @Override
            protected int exec(String cmd, List<String> args, Map<String, String> env) {
                return commands.exec(cmd, args, env);
            }
        }, project, "1.0.0");
    }

    @Test
    void onlyChecksFormattingNeverRewritesAndStaysOutOfTheBuildDirectory() throws Exception {
        mojo().execute();

        assertEquals(List.of("uv", "sync", "--locked"), commands.commands.get(0));
        assertEquals(List.of("uv", "run", "--no-sync", "--with", "ruff", "ruff", "format", "--check",
                "--config", "extend-exclude = [\"" + project.resolve("target") + "\"]"), commands.commands.get(1));
    }

    @Test
    void skipFlagSkipsTheCheck() throws Exception {
        FormatMojo mojo = mojo();
        set(mojo, "skip", true);

        mojo.execute();

        assertTrue(commands.commands.isEmpty());
        assertEquals(List.of("Format check is skipped."), log(mojo).info);
    }

    @Test
    void failsTheBuildWhenTheProjectFailsTheCheck() {
        commands.exitingWith(0, 1);

        MojoFailureException e = assertThrows(MojoFailureException.class, () -> mojo().execute());

        assertEquals("ruff found files that aren't formatted. Run `ruff format` to fix them.", e.getMessage());
    }

    @Test
    void reportsABuildErrorWhenTheToolCantRun() {
        commands.exitingWith(0, 2);

        MojoExecutionException e = assertThrows(MojoExecutionException.class, () -> mojo().execute());

        assertEquals("`uv run ruff format` failed with exit code 2. See the output above.", e.getMessage());
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
