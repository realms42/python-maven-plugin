package com.github.realms42.mojos;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "test", defaultPhase = LifecyclePhase.TEST)
public class TestMojo extends BaseMojo {

    private static final int PYTEST_NO_TESTS_COLLECTED = 5;

    @Parameter(property = "skipTests", defaultValue = "false")
    private boolean skipTests;

    @Parameter(property = "maven.test.skip", defaultValue = "false")
    private boolean skip;

    @Override
    public void execute() throws MojoExecutionException {
        if (skip || skipTests) {
            getLog().info("Tests are skipped.");
            return;
        }
        String cacheDir = "cache_dir=" + getPythonMavenPath().resolve("pytest-cache");
        int exitCode = exec("uv", uvRun("pytest", "-o", cacheDir), uvEnvironment());
        if (exitCode == PYTEST_NO_TESTS_COLLECTED) {
            getLog().info("No tests to run.");
        } else if (exitCode != 0) {
            throw new MojoExecutionException("Command failed with exit code " + exitCode);
        }
    }
}
