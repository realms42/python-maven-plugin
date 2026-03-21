package com.example.mojos;

import java.io.File;
import java.nio.file.Path;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Parameter;

abstract class BaseMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project.build.directory}", readonly = true)
    protected File buildDir;

    @Parameter(defaultValue = "${project.basedir}", readonly = true)
    protected File basePath;

    protected Path getPythonBuildPath() {
        return buildDir.toPath().resolve("python-maven-plugin");
    }

    protected Path getVenvPath() {
        return getPythonBuildPath().resolve("venv");
    }

    protected Path venvBin(String executable) {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        String bin = isWindows ? "Scripts" : "bin";
        return getVenvPath().resolve(bin).resolve(executable);
    }

}
