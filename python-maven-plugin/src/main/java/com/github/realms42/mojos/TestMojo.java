package com.github.realms42.mojos;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "test", defaultPhase = LifecyclePhase.TEST)
public class TestMojo extends BaseMojo {

    @Parameter(defaultValue = "pytest")
    private String tester;

    @Parameter(property = "python.test.skip", defaultValue = "false")
    private boolean skipTests;

    @Override
    public void execute() throws MojoExecutionException {
        if (skipTests) {
            getLog().info("Tests skipped.");
            return;
        }
        runInVenv(tester);
        getLog().info("Testing completed with " + tester);
    }
}
