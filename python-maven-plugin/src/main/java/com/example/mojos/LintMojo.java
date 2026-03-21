package com.example.mojos;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import com.example.util.Process;

@Mojo(name = "lint", defaultPhase = LifecyclePhase.TEST)
public class LintMojo extends BaseMojo {

    @Parameter(defaultValue = "flake8")
    private String linter;

    @Override
    public void execute() throws MojoExecutionException {
        Process.run(getPythonBuildPath(), venvBin(linter).toString());
        getLog().info("Linting completed with " + linter);
    }
}
