package com.github.realms42.mojos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "lint", defaultPhase = LifecyclePhase.TEST)
public class LintMojo extends BaseMojo {
    @Parameter(property = "python.lint.select", defaultValue = "E,F,W,I,UP")
    private String lintSelect;

    @Parameter(property = "python.lint.ignore", defaultValue = "")
    private String lintIgnore;

    @Parameter(property = "python.lint.skip", defaultValue = "false")
    private boolean skipLint;

    @Override
    public void execute() throws MojoExecutionException {
        if (skipLint) {
            getLog().info("Linting skipped.");
            return;
        }

        List<String> cmd = new ArrayList<>(Arrays.asList("ruff", "check", "--select", lintSelect));
        getLog().info("Selected rules: " + lintSelect);
        if (lintIgnore != null && !lintIgnore.isBlank()) {
            cmd.add("--ignore");
            cmd.add(lintIgnore);
            getLog().info("Ignored rules: " + lintIgnore);
        }
        cmd.add(".");

        runInVenv(cmd);
        getLog().info("Linting check completed.");
    }
}
