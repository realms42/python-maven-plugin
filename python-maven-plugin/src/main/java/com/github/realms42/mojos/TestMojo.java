package com.github.realms42.mojos;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Runs the project's tests with pytest. The results are also written as a JUnit XML report to
 * {@code target/surefire-reports/TEST-pytest.xml}, where CI servers look for Maven test results.
 * When pytest collects no tests, the goal passes, as Surefire does.
 */
@Mojo(name = "test", defaultPhase = LifecyclePhase.TEST, threadSafe = true)
public class TestMojo extends BaseMojo {

    private static final int PYTEST_NO_TESTS_COLLECTED = 5;

    /** Skips running the tests, like Surefire's {@code skipTests}. */
    @Parameter(property = "skipTests", defaultValue = "false")
    private boolean skipTests;

    /** Skips running the tests, like Surefire's {@code maven.test.skip}. */
    @Parameter(property = "maven.test.skip", defaultValue = "false")
    private boolean skip;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip || skipTests) {
            getLog().info("Tests are skipped.");
            return;
        }
        String cacheDir = "cache_dir=" + getPythonMavenPath().resolve("pytest-cache");
        String ignoreBuildDir = "--ignore=" + buildDir.getAbsolutePath();
        String report = "--junitxml=" + buildDir.toPath().resolve("surefire-reports").resolve("TEST-pytest.xml");
        uvSync();
        int exitCode = exec("uv", uvRun("pytest", "-o", cacheDir, ignoreBuildDir, report), uvEnvironment());
        if (exitCode == PYTEST_NO_TESTS_COLLECTED) {
            getLog().info("No tests to run.");
            return;
        }
        checkExitCode("pytest", "There are test failures. See pytest's output above.", exitCode);
    }
}
