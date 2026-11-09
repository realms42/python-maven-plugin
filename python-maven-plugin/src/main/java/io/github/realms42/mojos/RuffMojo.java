package io.github.realms42.mojos;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;

public abstract class RuffMojo extends BaseMojo {

    /** The ruff version to run, whatever the project pins. */
    @Parameter(property = "python.ruff.version", defaultValue = RUFF_VERSION)
    private String ruffVersion;

    /** Ruff settings with TOML values, passed as {@code --config}, overriding the project's own values. */
    @Parameter
    private Map<String, String> ruffSettings;

    protected void runRuff(String name, String failure, List<String> subcommand, List<String> args)
            throws MojoExecutionException, MojoFailureException {
        List<String> command = new ArrayList<>(subcommand);
        command.add("--config");
        command.add(ruffExcludeBuildDir());
        for (Map.Entry<String, String> setting : settings(ruffSettings)) {
            if (setting.getValue().isEmpty()) {
                throw new MojoExecutionException("The ruffSettings entry <" + setting.getKey() + "> has no value."
                        + " Give it a TOML value, such as 100, \"py311\" or [\"E\", \"F\"].");
            }
            command.add("--config");
            command.add(setting.getKey() + " = " + setting.getValue());
        }
        command.addAll(args);
        Path ruff = installTool(getToolsVenvPath(), "ruff", ruffVersion);
        runCheck(name, failure, ruff, command);
    }
}
