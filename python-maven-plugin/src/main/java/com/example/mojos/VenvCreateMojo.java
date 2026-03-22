package com.example.mojos;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import com.example.util.CommandRunner;

@Mojo(name = "venv-create", defaultPhase = LifecyclePhase.PROCESS_SOURCES)
public class VenvCreateMojo extends BaseMojo {
    @Override
    public void execute() throws MojoExecutionException {
        CommandRunner.run(getWorkPath(), "python3", "-m", "venv", getVenvPath().toString());
        getLog().info("Created virtual environment at " + getVenvPath());
    }
}
