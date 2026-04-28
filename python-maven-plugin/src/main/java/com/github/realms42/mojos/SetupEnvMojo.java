package com.github.realms42.mojos;

import java.nio.file.Files;
import java.util.Arrays;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import com.github.realms42.util.CommandRunnerUtil;

@Mojo(name = "setup-env", defaultPhase = LifecyclePhase.GENERATE_RESOURCES)
public class SetupEnvMojo extends BaseMojo {

    @Parameter(defaultValue = "python3")
    private String python;

    @Override
    public void execute() throws MojoExecutionException {
        setupProjectVenv();
        setupToolsVenv();
    }

    private void setupProjectVenv() throws MojoExecutionException {
        if (!Files.exists(getProjectVenvPath())) {
            CommandRunnerUtil.run(getWorkPath(),
                    Arrays.asList(python, "-m", "venv", getProjectVenvPath().toString()));
            getLog().info("Created project venv at " + getProjectVenvPath());
        } else {
            getLog().info("Reusing project venv at " + getProjectVenvPath());
        }
        runInProjectVenv("python", "-m", "pip", "install", "--quiet",
                "--disable-pip-version-check", "-e", ".");
        getLog().info("Project venv ready (clean runtime environment)");
    }

    private void setupToolsVenv() throws MojoExecutionException {
        if (!Files.exists(getToolsVenvPath())) {
            CommandRunnerUtil.run(getWorkPath(),
                    Arrays.asList(python, "-m", "venv", getToolsVenvPath().toString()));
            getLog().info("Created tools venv at " + getToolsVenvPath());
        } else {
            getLog().info("Reusing tools venv at " + getToolsVenvPath());
        }
        runInVenv("python", "-m", "pip", "install", "--quiet",
                "--disable-pip-version-check", "build", "pytest", "ruff");
        runInVenv("python", "-m", "pip", "install", "--quiet",
                "--disable-pip-version-check", "-e", ".");
        getLog().info("Tools venv ready (build, pytest, ruff + project editable install)");
    }
}
