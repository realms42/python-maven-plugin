package com.github.realms42.mojos;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "format", defaultPhase = LifecyclePhase.TEST)
public class FormatMojo extends BaseMojo {

    @Parameter(property = "python.format.skip", defaultValue = "false")
    private boolean skipFormat;

    @Override
    public void execute() throws MojoExecutionException {
        if (skipFormat) {
            getLog().info("Format check skipped.");
            return;
        }
        runInVenv("ruff", "format", "--check", ".");
        getLog().info("Code formatting check completed.");
    }
}
