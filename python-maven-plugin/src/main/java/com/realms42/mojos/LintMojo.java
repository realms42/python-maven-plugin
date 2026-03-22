package com.realms42.mojos;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

import com.realms42.util.CommandRunner;

@Mojo(name = "lint", defaultPhase = LifecyclePhase.TEST)
public class LintMojo extends BaseMojo {
    @Override
    public void execute() throws MojoExecutionException {
        CommandRunner.run(getWorkPath(), getVenvBin("ruff").toString(), "check", ".");
        getLog().info("Linting check completed.");
    }
}
