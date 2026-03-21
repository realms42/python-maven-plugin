package com.example.mojos;

import java.nio.file.Path;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import com.example.util.Process;

@Mojo(name = "venv-create", defaultPhase = LifecyclePhase.PROCESS_SOURCES)
public class CreateVenvMojo extends BaseMojo {
    @Override
    public void execute() throws MojoExecutionException {
        Path venvPath = getVenvPath();
        Process.run(getPythonBuildPath(), "python3", "-m", "venv", venvPath.toString());
        getLog().info("Created virtual environment at " + venvPath);
        Process.run(getPythonBuildPath(), venvBin("pip").toString(), "install", "--upgrade", "pip");
    }
}
