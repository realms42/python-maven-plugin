package com.example.mojos;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

@Mojo(name = "create-venv", defaultPhase = LifecyclePhase.PROCESS_SOURCES)
public class CreateVenvMojo extends BaseMojo {
    @Override
    public void execute() throws MojoExecutionException {
        runProcess(buildPath, "python3", "-m", "venv", venvPath.toString());
        getLog().info("Created virtual environment at " + venvPath);
    }
}
