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
     * Optional extras to install with the package, e.g. "docs,extra".
     * Translates to "pip install -e .[extras]".
     */
    @Parameter
    private String extras;

    @Override
    public void execute() throws MojoExecutionException {
        if (!Files.exists(getVenvPath())) {
            CommandRunnerUtil.run(getWorkPath(),
                    Arrays.asList(python, "-m", "venv", getVenvPath().toString()));
            getLog().info("Created virtual environment at " + getVenvPath());
        } else {
            getLog().info("Reusing existing virtual environment at " + getVenvPath());
        }

        // Plugin tooling: pinned/owned by the plugin, not coupled to the user's pyproject.
        runInVenv("python", "-m", "pip", "install", "--quiet", "--disable-pip-version-check",
                "build", "pytest", "ruff");

        // Install the user's package in editable mode using the generated pyproject.toml.
        String spec = (extras != null && !extras.isBlank()) ? ".[" + extras + "]" : ".";
        runInVenv("python", "-m", "pip", "install", "--quiet", "--disable-pip-version-check",
                "-e", spec);

        getLog().info("Environment ready at " + getVenvPath());
    }
}
