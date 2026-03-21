package com.example.mojos;

import java.nio.file.Path;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

@Mojo(name = "build", defaultPhase = LifecyclePhase.COMPILE)
public class BuildMojo extends BaseMojo {

    @Override
    public void execute() throws MojoExecutionException {
        Path pythonBuildPath = getPythonBuildPath();
        runProcess(pythonBuildPath, venvBin("pip").toString(), "install", "--upgrade", "pip");
        runProcess(pythonBuildPath, venvBin("pip").toString(), "wheel", ".", "--no-deps", "-w", "dist");
    }
}
