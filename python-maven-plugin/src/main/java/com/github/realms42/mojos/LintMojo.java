package com.github.realms42.mojos;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "lint", defaultPhase = LifecyclePhase.TEST)
public class LintMojo extends BaseMojo {

    @Parameter(property = "python.lint.skip", defaultValue = "false")
    private boolean skipLint;

    @Override
    public void execute() throws MojoExecutionException {
        if (skipLint) {
            getLog().info("Linting skipped.");
            return;
        }
        runInVenv("ruff", "check", ".");
        getLog().info("Linting check completed.");
    }
}
