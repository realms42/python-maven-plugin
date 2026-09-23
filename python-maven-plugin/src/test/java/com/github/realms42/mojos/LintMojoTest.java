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
        mojo().execute();

        assertEquals(List.of("uv", "run", "--locked", "--with", "ruff", "ruff", "check",
                "--config", "extend-exclude = [\"" + project.resolve("target") + "\"]"), commands.commands.get(0));
        assertEquals(project.resolve("target/python-maven-plugin/ruff-cache").toString(),
                commands.environments.get(0).get("RUFF_CACHE_DIR"));
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
