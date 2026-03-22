package com.realms42.mojos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

import com.realms42.util.CommandRunner;

@Mojo(name = "install-deps", defaultPhase = LifecyclePhase.PROCESS_SOURCES)
public class InstallDevMojo extends BaseMojo {

    @Override
    public void execute() throws MojoExecutionException {
        List<String> cmd_dep = new ArrayList<>(
                Arrays.asList(getVenvBin("pip").toString(), "install", "build", "ruff", "pytest"));
        CommandRunner.run(getWorkPath(), cmd_dep);
        getLog().info("Installed build, ruff, and pytest in the virtual environment.");
        List<String> cmd_install = new ArrayList<>(
                Arrays.asList(getVenvBin("pip").toString(), "install", "-e", "."));
        CommandRunner.run(getWorkPath(), cmd_install);
        getLog().info("Installed package with dev dependencies.");
    }
}