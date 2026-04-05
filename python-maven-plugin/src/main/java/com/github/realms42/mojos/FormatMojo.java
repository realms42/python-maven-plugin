package com.github.realms42.mojos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

import com.github.realms42.util.CommandRunnerUtil;

@Mojo(name = "format", defaultPhase = LifecyclePhase.TEST)
public class FormatMojo extends BaseMojo {
    @Override
    public void execute() throws MojoExecutionException {
        List<String> cmd = new ArrayList<>(
                Arrays.asList(getVenvBin("ruff").toString(), "format", "--check", "."));
        CommandRunnerUtil.run(getWorkPath(), cmd);
        getLog().info("Code formatting check completed.");
    }
}
