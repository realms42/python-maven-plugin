package com.github.realms42.mojos;

import java.util.Arrays;
import java.util.Map;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

@Mojo(name = "build", defaultPhase = LifecyclePhase.COMPILE)
public class BuildMojo extends BaseMojo {

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        run(
                python,
                Arrays.asList("-m", "build", "--outdir", getPythonBuildPath().toString()),
                Map.of("PACKAGE_VERSION", versionToPep440()));
    }
}
