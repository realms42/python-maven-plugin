package com.github.realms42.mojos;

import java.io.IOException;
import java.nio.file.Path;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

import com.github.realms42.mojos.base.AbstractPythonMojo;
import com.github.realms42.util.FileCopyUtil;

@Mojo(name = "package", defaultPhase = LifecyclePhase.PACKAGE)
public class PackageMojo extends AbstractPythonMojo {

    @Override
    public void execute() throws MojoExecutionException {
        Path target = buildDir.toPath();
        try {
            for (Path artifact : FileCopyUtil.copyFiles(getBuildPath(), p -> target.resolve(p.getFileName()))) {
                getLog().info("Packaged: " + artifact.getFileName());
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to package artifacts: " + e.getMessage());
        }
    }
}
