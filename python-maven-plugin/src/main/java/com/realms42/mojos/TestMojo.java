package com.realms42.mojos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import com.realms42.util.CommandRunner;

@Mojo(name = "test", defaultPhase = LifecyclePhase.TEST)
public class TestMojo extends BaseMojo {

    @Parameter(defaultValue = "pytest")
    private String tester;

    @Override
    public void execute() throws MojoExecutionException {
        List<String> cmd = new ArrayList<>(
                Arrays.asList(getVenvBin(tester).toString()));
        CommandRunner.run(getWorkPath(), cmd);
        getLog().info("Testing completed with " + tester);
    }
}
