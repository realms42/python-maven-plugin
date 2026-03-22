package com.realms42.mojos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import com.realms42.util.Python;

@Mojo(name = "write-version", defaultPhase = LifecyclePhase.INITIALIZE)
public class WriteVersionMojo extends BaseMojo {

    @Parameter(defaultValue = "${project.version}", readonly = true)
    private String projectVersion;

    @Override
    public void execute() throws MojoExecutionException {
        String version = Python.versionFromMaven(projectVersion);
        Path versionPath = getWorkPath().resolve("VERSION");
        try {
            Files.write(versionPath, version.getBytes());
            getLog().info("Written version " + version + " to " + versionPath);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to write " + versionPath + ": " + e.getMessage());
        }
    }
}