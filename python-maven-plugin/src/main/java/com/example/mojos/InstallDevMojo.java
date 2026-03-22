package com.example.mojos;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import com.example.util.CommandRunner;

@Mojo(name = "install-deps", defaultPhase = LifecyclePhase.PROCESS_SOURCES)
public class InstallDevMojo extends BaseMojo {

    @Override
    public void execute() throws MojoExecutionException {
        CommandRunner.run(getSrcPath(), getVenvBin("pip").toString(), "install", "build", "ruff", "pytest");
        getLog().info("Installed build, ruff, and pytest in the virtual environment.");
        CommandRunner.run(getSrcPath(), getVenvBin("pip").toString(), "install", "-e", ".[dev]");
        getLog().info("Installed package with dev dependencies.");
    }
}