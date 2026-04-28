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

    /**
     * Optional extras to install with the project package, e.g. "docs,grpc".
     * These are installed into both venvs. Only use this for runtime/test extras
     * declared in [project.optional-dependencies] of the generated pyproject.toml.
     */
    @Parameter
    private String extras;

    @Override
    public void execute() throws MojoExecutionException {
        String spec = (extras != null && !extras.isBlank()) ? ".[" + extras + "]" : ".";

        setupProjectVenv(spec);
        setupToolsVenv(spec);
    }

    private void setupProjectVenv(String spec) throws MojoExecutionException {
        if (!Files.exists(getProjectVenvPath())) {
            CommandRunnerUtil.run(getWorkPath(),
                    Arrays.asList(python, "-m", "venv", getProjectVenvPath().toString()));
            getLog().info("Created project venv at " + getProjectVenvPath());
        } else {
            getLog().info("Reusing project venv at " + getProjectVenvPath());
        }
        // Install only the project and its declared dependencies — no plugin tooling.
        runInProjectVenv("python", "-m", "pip", "install", "--quiet",
                "--disable-pip-version-check", "-e", spec);
        getLog().info("Project venv ready (clean runtime environment)");
    }

    private void setupToolsVenv(String spec) throws MojoExecutionException {
        if (!Files.exists(getToolsVenvPath())) {
            CommandRunnerUtil.run(getWorkPath(),
                    Arrays.asList(python, "-m", "venv", getToolsVenvPath().toString()));
            getLog().info("Created tools venv at " + getToolsVenvPath());
        } else {
            getLog().info("Reusing tools venv at " + getToolsVenvPath());
        }
        // Install plugin tooling.
        runInVenv("python", "-m", "pip", "install", "--quiet",
                "--disable-pip-version-check", "build", "pytest", "ruff");
        // Install the project editable so tests can import it.
        runInVenv("python", "-m", "pip", "install", "--quiet",
                "--disable-pip-version-check", "-e", spec);
        getLog().info("Tools venv ready (build, pytest, ruff + project editable install)");
    }
}
