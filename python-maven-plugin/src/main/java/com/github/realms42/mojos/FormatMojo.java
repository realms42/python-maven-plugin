package com.github.realms42.mojos;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "format", defaultPhase = LifecyclePhase.VERIFY)
public class FormatMojo extends BaseMojo {

    @Parameter(property = "python.format.skip", defaultValue = "false")
    private boolean skip;

    @Override
    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("Format check is skipped.");
            return;
        }
        run("uv", uvRun("ruff", "format", "--check"), uvEnvironment());
    }
}
