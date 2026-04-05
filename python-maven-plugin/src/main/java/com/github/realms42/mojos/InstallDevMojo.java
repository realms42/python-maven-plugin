package com.github.realms42.mojos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

import com.github.realms42.util.CommandRunnerUtil;

@Mojo(name = "install-deps", defaultPhase = LifecyclePhase.PROCESS_SOURCES)
public class InstallDevMojo extends BaseMojo {

    @Override
    public void execute() throws MojoExecutionException {
        List<String> cmd_install = new ArrayList<>(
                Arrays.asList(getVenvBin("pip").toString(), "install", "-e", ".[test,build]"));
        CommandRunnerUtil.run(getWorkPath(), cmd_install);
        getLog().info("Installed package in editable mode with test and build dependencies");
    }
}