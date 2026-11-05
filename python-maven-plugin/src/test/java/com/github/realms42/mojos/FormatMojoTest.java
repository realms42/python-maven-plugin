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
        FormatMojo mojo = configure(new FormatMojo() {
            @Override
            protected int exec(String cmd, List<String> args, Map<String, String> env) {
                return commands.exec(cmd, args, env);
            }
        }, project, "1.0.0");
        set(mojo, "ruffVersion", "0.17.3");
        return mojo;
    }

    @Test
    void onlyChecksFormattingWithThePluginsPinnedRuffNeverRewritesAndStaysOutOfTheBuildDirectory()
            throws Exception {
        mojo().execute();

        Path tools = project.resolve("target/python-maven-plugin/tools/.venv");
        assertEquals(List.of(
                List.of("uv", "venv", tools.toString()),
                List.of("uv", "pip", "install", "--python", tools.toString(), "ruff==0.17.3"),
                List.of(BaseMojo.venvExecutable(tools, "ruff").toString(), "format", "--check",
                        "--config", "extend-exclude = [\"" + project.resolve("target") + "\"]")),
                commands.commands);
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
    void failsTheBuildWhenFilesArentFormatted() {
        commands.exitingWith(0, 0, 1);

        MojoFailureException e = assertThrows(MojoFailureException.class, () -> mojo().execute());

        assertEquals("ruff found files that aren't formatted. Run `ruff format` to fix them.", e.getMessage());
    }

    @Test
    void reportsABuildErrorWhenRuffCantRun() {
        commands.exitingWith(0, 0, 2);

        MojoExecutionException e = assertThrows(MojoExecutionException.class, () -> mojo().execute());

        assertEquals("`ruff format` failed with exit code 2. See the output above.", e.getMessage());
    }

    @Test
    void reportsABuildErrorWithoutCheckingWhenRuffCantBeInstalled() {
        commands.exitingWith(0, 1);

        MojoExecutionException e = assertThrows(MojoExecutionException.class, () -> mojo().execute());

        assertEquals("`uv pip` failed with exit code 1. See the output above.", e.getMessage());
        assertEquals(2, commands.commands.size());
    }
}
