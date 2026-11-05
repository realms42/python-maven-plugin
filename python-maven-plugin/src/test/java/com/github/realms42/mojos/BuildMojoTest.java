package com.github.realms42.mojos;

import static com.github.realms42.mojos.MojoTesting.configure;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.github.realms42.mojos.MojoTesting.Commands;

class BuildMojoTest {

    @TempDir
    Path project;

    private final Commands commands = new Commands();

    private BuildMojo mojo() {
        return configure(new BuildMojo() {
            @Override
            protected int exec(String cmd, List<String> args, Map<String, String> env) {
                return commands.exec(cmd, args, env);
            }
        }, project, "3.1.0-SNAPSHOT");
    }

    @Test
    void buildsWithUvIntoPythonBuild() throws Exception {
        mojo().execute();

        assertEquals(
                List.of("uv", "build", "--out-dir", project.resolve("target/python-build").toString()),
                commands.commands.get(0));
        assertEquals("3.1.0.dev0", commands.environments.get(0).get("SETUPTOOLS_SCM_PRETEND_VERSION"));
        assertEquals("3.1.0.dev0", commands.environments.get(0).get("PDM_BUILD_SCM_VERSION"));
    }

    @Test
    void clearsAnEarlierBuildsDistributionsBeforeBuilding() throws Exception {
        Path output = Files.createDirectories(project.resolve("target/python-build"));
        Files.writeString(output.resolve("example-0.9.0.tar.gz"), "stale");
        Files.createDirectories(output.resolve("nested"));
        Files.writeString(output.resolve("nested/leftover"), "stale");
        BuildMojo mojo = configure(new BuildMojo() {
            @Override
            protected int exec(String cmd, List<String> args, Map<String, String> env) {
                try {
                    Files.createDirectories(output);
                    Files.writeString(output.resolve("example-1.0.0.tar.gz"), "fresh");
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                return 0;
            }
        }, project, "1.0.0");

        mojo.execute();

        try (Stream<Path> files = Files.list(output)) {
            assertEquals(List.of("example-1.0.0.tar.gz"),
                    files.map(p -> p.getFileName().toString()).collect(Collectors.toList()));
        }
    }

    @Test
    void failsWhenUvBuildFails() {
        commands.exitingWith(2);

        MojoExecutionException e = assertThrows(MojoExecutionException.class, () -> mojo().execute());

        assertEquals("`uv build` failed with exit code 2. See the output above.", e.getMessage());
    }
}
