package com.github.realms42.mojos;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

import com.github.realms42.mojos.base.AbstractPythonMojo;
import com.github.realms42.util.CommandRunnerUtil;

@Mojo(name = "setup-env", defaultPhase = LifecyclePhase.GENERATE_RESOURCES)
public class SetupEnvMojo extends AbstractPythonMojo {

    private static final List<String> PIP_INSTALL = Arrays.asList(
            "-m", "pip", "install", "--quiet", "--disable-pip-version-check");

    @Override
    public void execute() throws MojoExecutionException {
        createVenv(getProjectVenvPath(), "project");
        runInProjectVenv(python, pipInstall("-e", "."));
        getLog().info("Project venv ready (clean runtime environment)");

        createVenv(getPluginVenvPath(), "tools");
        runInVenv(python, pipInstall("build", "pytest", "ruff"));
        runInVenv(python, pipInstall("-e", "."));
        getLog().info("Tools venv ready (build, pytest, ruff + project editable install)");
    }

    private void createVenv(Path venvPath, String label) throws MojoExecutionException {
        if (Files.exists(venvPath)) {
            getLog().info("Reusing " + label + " venv at " + venvPath);
            return;
        }
        CommandRunnerUtil.run(getWorkPath(), Arrays.asList(python, "-m", "venv", venvPath.toString()));
        getLog().info("Created " + label + " venv at " + venvPath);
    }

    private static String[] pipInstall(String... targets) {
        List<String> args = new ArrayList<>(PIP_INSTALL);
        args.addAll(Arrays.asList(targets));
        return args.toArray(new String[0]);
    }
}
