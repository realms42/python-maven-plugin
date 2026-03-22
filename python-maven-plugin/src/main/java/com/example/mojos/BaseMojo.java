package com.example.mojos;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Parameter;

abstract class BaseMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project.build.directory}", readonly = true)
    protected File buildDir;

    @Parameter(defaultValue = "${project.basedir}", readonly = true)
    protected File basePath;

    protected Path getPythonMavenPath() {
        return buildDir.toPath().resolve("python-maven-plugin");
    }

    protected Path getWorkPath() {
        return getPythonMavenPath().resolve("workdir");
    }

    protected Path getBuildPath() {
        return getPythonMavenPath().resolve("build");
    }

    protected Path getVenvPath() {
        return getPythonMavenPath().resolve("venv");
    }

    protected Path getVenvBin(String executable) {
        String binDir = System.getProperty("os.name").toLowerCase().contains("win") ? "Scripts" : "bin";
        return getVenvPath().resolve(binDir).resolve(executable);
    }

}
