package com.github.realms42.mojos;

import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Checks the project's formatting with {@code ruff format --check}, using the project's own ruff configuration
 * plus the {@code ruffSettings}. Runs the plugin's pinned ruff ({@code ruffVersion}), not the project's. Never
 * rewrites a file: it reports what isn't formatted and fails the build.
 */
@Mojo(name = "format", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public class FormatMojo extends RuffMojo {

    /** Skips the format check. */
    @Parameter(property = "python.format.skip", defaultValue = "false")
    private boolean skip;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Format check is skipped.");
            return;
        }
        runRuff("ruff format", "ruff found files that aren't formatted. Run `ruff format` to fix them.",
                List.of("format", "--check"), List.of());
    }
}
