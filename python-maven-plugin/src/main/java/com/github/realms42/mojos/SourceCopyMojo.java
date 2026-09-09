package com.github.realms42.mojos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import com.github.realms42.mojos.base.AbstractPythonMojo;
import com.github.realms42.util.FileCopyUtil;

@Mojo(name = "copy-sources", defaultPhase = LifecyclePhase.INITIALIZE)
public class SourceCopyMojo extends AbstractPythonMojo {

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
                FileCopyUtil.copyFiles(source, p -> target.resolve(source.relativize(p)));
                getLog().info("Copied " + source + " -> " + target);
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to copy sources: " + e.getMessage());
        }
    }
}
