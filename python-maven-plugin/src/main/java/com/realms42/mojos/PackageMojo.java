package com.realms42.mojos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

@Mojo(name = "package", defaultPhase = LifecyclePhase.PACKAGE)
public class PackageMojo extends BaseMojo {

    @Override
    public void execute() throws MojoExecutionException {
        try {
            Files.walk(getBuildPath())
                    .filter(p -> !Files.isDirectory(p))
                    .forEach(p -> {
                        try {
                            Files.copy(p, buildDir.toPath().resolve(p.getFileName()),
                                    StandardCopyOption.REPLACE_EXISTING);
                            getLog().info("Packaged: " + p.getFileName());
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to package artifacts: " + e.getMessage());
        }
    }
}