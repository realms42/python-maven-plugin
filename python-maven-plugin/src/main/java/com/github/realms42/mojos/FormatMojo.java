package com.github.realms42.mojos;

import java.nio.file.Path;
import java.util.Arrays;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Checks the project's formatting with {@code ruff format --check}, using the project's own ruff configuration.
 * Runs the plugin's pinned ruff ({@code ruffVersion}), not the project's. Never rewrites a file: it reports what
 * isn't formatted and fails the build.
 */
@Mojo(name = "format", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public class FormatMojo extends BaseMojo {

    /** Skips the format check. */
    @Parameter(property = "python.format.skip", defaultValue = "false")
    private boolean skip;

    /**
     * The exact ruff version the plugin runs, from its own environment under {@code target/}. A project's own
     * ruff dependency is for running ruff itself, outside Maven, and isn't used here.
     */
    @Parameter(property = "python.ruff.version", defaultValue = RUFF_VERSION)
    private String ruffVersion;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Format check is skipped.");
            return;
        }
        Path ruff = installTool(getToolsVenvPath(), "ruff", ruffVersion);
        runCheck("ruff format", "ruff found files that aren't formatted. Run `ruff format` to fix them.", ruff,
                Arrays.asList("format", "--check", "--config", ruffExcludeBuildDir()));
    }
}
