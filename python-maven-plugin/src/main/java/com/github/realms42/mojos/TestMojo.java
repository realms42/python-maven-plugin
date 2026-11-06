package com.github.realms42.mojos;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Runs the project's tests with the plugin's pinned pytest ({@code pytestVersion}), using the project's own
 * pytest configuration plus the {@code pytestSettings}. The results are also written as a JUnit XML report to
 * {@code target/surefire-reports/TEST-pytest.xml}, where CI servers look for Maven test results. When pytest
 * collects no tests, the goal passes, as Surefire does.
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

    /**
     * The exact pytest version the plugin runs. It is installed into the plugin's environment for the project,
     * under {@code target/}, over any version the project's lock pins; the project's own pytest is for running
     * the tests itself, outside Maven. Test dependencies and pytest plugins still come from the project's lock.
     */
    @Parameter(property = "python.pytest.version", defaultValue = PYTEST_VERSION)
    private String pytestVersion;

    /**
     * pytest options applied on top of the project's own pytest configuration. Each key is a pytest
     * configuration option, as in {@code [tool.pytest.ini_options]}, and each value is that option's text, as
     * pytest reads it:
     *
     * <pre>
     * &lt;pytestSettings&gt;
     *   &lt;xfail_strict&gt;true&lt;/xfail_strict&gt;
     *   &lt;addopts&gt;--strict-markers&lt;/addopts&gt;
     * &lt;/pytestSettings&gt;
     * </pre>
     *
     * Each entry is passed as {@code -o name=value}, which overrides the project's own value for that option
     * ({@code addopts} replaces the project's {@code addopts}); every other option stays the project's. Set it in
     * a parent POM to configure many projects at once: a child POM inherits each entry and can override single
     * entries.
     */
    @Parameter
    private Map<String, String> pytestSettings;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip || skipTests) {
            getLog().info("Tests are skipped.");
            return;
        }
        List<String> args = new ArrayList<>();
        for (Map.Entry<String, String> setting
                : new TreeMap<>(pytestSettings == null ? Map.of() : pytestSettings).entrySet()) {
            String value = setting.getValue() == null ? "" : setting.getValue().trim();
            args.add("-o");
            args.add(setting.getKey().trim() + "=" + value);
        }
        // The plugin's own options come last, so the settings can't move the cache or report out of target/.
        args.addAll(Arrays.asList(
                "-o", "cache_dir=" + getPythonMavenPath().resolve("pytest-cache"),
                "--ignore=" + buildDir.getAbsolutePath(),
                "--junitxml=" + buildDir.toPath().resolve("surefire-reports").resolve("TEST-pytest.xml")));
        uvSync();
        Path pytest = installTool(getProjectVenvPath(), "pytest", pytestVersion);
        int exitCode = exec(pytest.toString(), args, uvEnvironment());
        if (exitCode == PYTEST_NO_TESTS_COLLECTED) {
            getLog().info("No tests to run.");
            return;
        }
        checkExitCode("pytest", "There are test failures. See pytest's output above.", exitCode);
    }
}
