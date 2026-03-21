package com.example.mojos;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import com.example.util.Process;

@Mojo(name = "test", defaultPhase = LifecyclePhase.TEST)
public class TestMojo extends BaseMojo {

    @Parameter(defaultValue = "pytest")
    private String tester;

    @Override
    public void execute() throws MojoExecutionException {
        Process.run(getPythonBuildPath(), venvBin(tester).toString());
    }
}
