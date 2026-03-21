package com.example.mojos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import com.example.util.PepVersion;

@Mojo(name = "write-version", defaultPhase = LifecyclePhase.PROCESS_SOURCES)
public class WriteVersionMojo extends BaseMojo {

    @Parameter(defaultValue = "${project.version}", readonly = true)
    private String projectVersion;

    @Override
    public void execute() throws MojoExecutionException {
        String version = PepVersion.fromMaven(projectVersion);
        Path versionPath = getPythonBuildPath().resolve("VERSION");
        try {
            Files.write(versionPath, version.getBytes());
            getLog().info("Written version " + version + " to " + versionPath);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to write " + versionPath + ": " + e.getMessage());
        }
    }
}