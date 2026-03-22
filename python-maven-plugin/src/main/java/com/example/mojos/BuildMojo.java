package com.example.mojos;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import com.example.util.CommandRunner;

@Mojo(name = "build", defaultPhase = LifecyclePhase.COMPILE)
public class BuildMojo extends BaseMojo {

    @Override
    public void execute() throws MojoExecutionException {
        CommandRunner.run(getSrcPath(), getVenvBin("python3").toString(), "-m", "build", "--outdir",
                getBuildPath().toString());
    }
}
