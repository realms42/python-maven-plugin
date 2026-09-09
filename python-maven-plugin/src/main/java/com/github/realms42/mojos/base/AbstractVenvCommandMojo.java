package com.github.realms42.mojos.base;

import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;

/**
 * Template for goals whose whole job is "run one tool in the plugin tools venv".
 *
 * <p>Maven binds exactly one goal per {@code @Mojo} class, so lint, format, test
 * and build cannot be a single class instantiated several times. They can,
 * however, share their entire body: a subclass only declares its goal
 * annotation, its own parameters, and the three hooks below.
 */
public abstract class AbstractVenvCommandMojo extends AbstractPythonMojo {

    /** The command to run: the venv program name first, then its arguments. */
    protected abstract List<String> command();

    /** Human-readable name of the work, used in the skip and completion log lines. */
    protected abstract String label();

    /** Whether the goal's own skip parameter is set. Goals without one never skip. */
    protected boolean isSkipped() {
        return false;
    }

    @Override
    public final void execute() throws MojoExecutionException {
        if (isSkipped()) {
            getLog().info(label() + " skipped.");
            return;
        }
        runInVenv(command());
        getLog().info(label() + " completed.");
    }
}
