package io.github.realms42.mojos;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Runs the tests with pytest against the built wheel, with a JUnit report in {@code target/surefire-reports/}.
 */
@Mojo(name = "test", defaultPhase = LifecyclePhase.TEST, threadSafe = true)
public class TestMojo extends BaseMojo {

    private static final int PYTEST_NO_TESTS_COLLECTED = 5;

    /** Skips the tests. */
    @Parameter(property = "skipTests", defaultValue = "false")
    private boolean skipTests;

    /** Skips the tests. */
    @Parameter(property = "maven.test.skip", defaultValue = "false")
    private boolean skip;

    /** The pytest version to run, whatever the project's lock pins. */
    @Parameter(property = "python.pytest.version", defaultValue = PYTEST_VERSION)
    private String pytestVersion;

    /** Tests the built wheel; {@code false} tests the source tree instead. */
    @Parameter(property = "python.test.wheel", defaultValue = "true")
    private boolean testWheel;

    /** pytest options, passed as {@code -o name=value}, overriding the project's own values. */
    @Parameter
    private Map<String, String> pytestSettings;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip || skipTests) {
            getLog().info("Tests are skipped.");
            return;
        }
        List<String> args = new ArrayList<>();
        for (Map.Entry<String, String> setting : settings(pytestSettings)) {
            args.add("-o");
            args.add(setting.getKey() + "=" + setting.getValue());
        }
        args.addAll(Arrays.asList(
                "-o", "cache_dir=" + getPythonMavenPath().resolve("pytest-cache"),
                "--ignore=" + buildDir.getAbsolutePath(),
                "--junitxml=" + buildDir.toPath().resolve("surefire-reports").resolve("TEST-pytest.xml")));
        Path pytest;
        if (testWheel) {
            Path wheel = builtWheel();
            uvSync("--no-install-project");
            pytest = installTool(getProjectVenvPath(), "pytest", pytestVersion);
            pipInstall(getProjectVenvPath(), "--no-deps", "--reinstall", wheel.toString());
            getLog().info("Testing the built wheel: " + wheel.getFileName());
        } else {
            uvSync();
            pytest = installTool(getProjectVenvPath(), "pytest", pytestVersion);
            getLog().info("Testing the source tree.");
        }
        int exitCode = exec(pytest.toString(), args, uvEnvironment());
        if (exitCode == PYTEST_NO_TESTS_COLLECTED) {
            getLog().info("No tests to run.");
            return;
        }
        checkExitCode("pytest", "There are test failures. See pytest's output above.", exitCode);
    }

    private Path builtWheel() throws MojoExecutionException {
        List<Path> wheels = built(WHEEL);
        if (wheels.size() != 1) {
            throw new MojoExecutionException("Expected exactly one wheel in " + getPythonBuildPath() + " to test,"
                    + " found " + wheels.size() + ". Run the build goal first, or test the source tree with"
                    + " -Dpython.test.wheel=false.");
        }
        return wheels.get(0);
    }
}
