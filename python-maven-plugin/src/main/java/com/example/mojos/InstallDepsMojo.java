package com.example.mojos;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

@Mojo(name = "install-deps", defaultPhase = LifecyclePhase.PROCESS_SOURCES)
public class InstallDepsMojo extends BaseMojo {

    @Override
    public void execute() throws MojoExecutionException {
        runProcess(getPythonBuildPath(), venvBin("pip").toString(), "install", "-e", ".[dev]");
    }
}