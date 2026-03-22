package com.example.mojos;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import com.example.util.CommandRunner;

@Mojo(name = "format", defaultPhase = LifecyclePhase.VERIFY)
public class FormatMojo extends BaseMojo {
    @Override
    public void execute() throws MojoExecutionException {
        CommandRunner.run(getSrcPath(), getVenvBin("ruff").toString(), "format", "--check", ".");
        getLog().info("Code formatting check completed.");
    }
}
