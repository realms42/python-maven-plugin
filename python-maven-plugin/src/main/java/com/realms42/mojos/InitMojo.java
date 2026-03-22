package com.realms42.mojos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

@Mojo(name = "initialize", defaultPhase = LifecyclePhase.INITIALIZE)
public class InitMojo extends BaseMojo {
    @Override
    public void execute() throws MojoExecutionException {
        if (!Files.exists(basePath.toPath())) {
            throw new MojoExecutionException("Base directory " + basePath + " does not exist");
        }
        Path workPath = getWorkPath();
        if (!Files.exists(workPath)) {
            try {
                Files.createDirectories(workPath);
                getLog().info("Created build directory at " + workPath);
            } catch (IOException e) {
                throw new MojoExecutionException(
                        "Failed to create build directory " + workPath + ": " + e.getMessage());
            }
        }
    }
}