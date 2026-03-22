package com.example.mojos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

@Mojo(name = "copy-sources", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class SourceCopyMojo extends BaseMojo {

    @Override
    public void execute() throws MojoExecutionException {
        try {
            Path srcPath = getSrcPath();
            copyDirectory(basePath.toPath(), srcPath);
            getLog().info("Copied sources to " + srcPath);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to copy sources: " + e.getMessage());
        }
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        Files.walk(source)
                .filter(p -> !p.startsWith(target))
                .filter(p -> !p.startsWith(buildDir.toPath()))
                .filter(p -> !p.startsWith("."))
                .forEach(p -> {
                    try {
                        Path dest = target.resolve(source.relativize(p));
                        if (Files.isDirectory(p)) {
                            Files.createDirectories(dest);
                        } else {
                            Files.copy(p, dest, StandardCopyOption.REPLACE_EXISTING);
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
    }
}
