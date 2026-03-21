package com.example.mojos;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

@Mojo(name = "build", defaultPhase = LifecyclePhase.COMPILE)
public class BuildMojo extends BaseMojo {

    @Override
    public void execute() throws MojoExecutionException {
        runProcess(buildPath, venvBin("pip").toString(), "install", "--upgrade", "pip");
        runProcess(buildPath, venvBin("pip").toString(), "wheel", ".", "--no-deps", "-w", "dist");
    }
}
