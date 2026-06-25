package com.github.realms42.mojos;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "build", defaultPhase = LifecyclePhase.COMPILE)
public class BuildMojo extends BaseMojo {

    @Parameter(defaultValue = "python3")
    private String python;

    @Override
    public void execute() throws MojoExecutionException {
        runInVenv(python, "-m", "build", "--outdir", getBuildPath().toString());
    }
}
