package com.realms42.mojos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

import com.realms42.util.CommandRunner;

@Mojo(name = "venv-create", defaultPhase = LifecyclePhase.PROCESS_SOURCES)
public class VenvCreateMojo extends BaseMojo {
    @Override
    public void execute() throws MojoExecutionException {
        List<String> cmd = new ArrayList<>(
                Arrays.asList("python3", "-m", "venv", getVenvPath().toString()));
        CommandRunner.run(getWorkPath(), cmd);
        getLog().info("Created virtual environment at " + getVenvPath());
    }
}
