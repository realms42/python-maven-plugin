package com.github.realms42.mojos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "copy-sources", defaultPhase = LifecyclePhase.INITIALIZE)
public class SourceCopyMojo extends BaseMojo {

    @Parameter
    private List<String> sourcePaths = Arrays.asList("src", "tests");

    @Override
    public void execute() throws MojoExecutionException {
        Path workPath = getWorkPath();
        try {
            Files.createDirectories(workPath);
            for (String relative : sourcePaths) {
                Path source = basePath.toPath().resolve(relative);
                if (!Files.exists(source)) {
                    getLog().info("Skipping non-existent source path: " + source);
                    continue;
                }
                Path target = workPath.resolve(relative);
                copyDirectory(source, target);
                getLog().info("Copied " + source + " -> " + target);
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to copy sources: " + e.getMessage());
        }
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        Files.walk(source).forEach(p -> {
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
