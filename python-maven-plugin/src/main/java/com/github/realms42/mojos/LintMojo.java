package com.github.realms42.mojos;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "lint", defaultPhase = LifecyclePhase.VERIFY)
public class LintMojo extends BaseMojo {

    @Parameter(property = "python.lint.skip", defaultValue = "false")
    private boolean skip;

    @Override
    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("Linting is skipped.");
            return;
        }
        run("uv", uvRun("ruff", "check"), uvEnvironment());
    }
}
