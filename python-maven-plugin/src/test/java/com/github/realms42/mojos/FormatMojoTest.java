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
    void onlyChecksFormattingAndNeverRewrites() throws Exception {
        mojo().execute();

        assertEquals(List.of("uv", "run", "--locked", "--with", "ruff", "ruff", "format", "--check"),
                commands.commands.get(0));
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
    void failsOnUnformattedFiles() {
        commands.exitingWith(1);

        MojoExecutionException e = assertThrows(MojoExecutionException.class, () -> mojo().execute());

        assertEquals("Command failed with exit code 1", e.getMessage());
    }
}
