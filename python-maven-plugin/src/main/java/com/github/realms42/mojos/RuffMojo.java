package com.github.realms42.mojos;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;

// The parameters and setup shared by the goals that run ruff, so a parent POM configures both in one place.
public abstract class RuffMojo extends BaseMojo {

    /**
     * The exact ruff version the plugin runs, from its own environment under {@code target/}. A project's own
     * ruff dependency is for running ruff itself, outside Maven, and isn't used here.
     */
    @Parameter(property = "python.ruff.version", defaultValue = RUFF_VERSION)
    private String ruffVersion;

    /**
     * Ruff settings applied on top of each project's own ruff configuration, for both {@code lint} and
     * {@code format}. Each key is a ruff setting name, dotted for nested sections, and each value is TOML, so
     * strings need quotes:
     *
     * <pre>
     * &lt;ruffSettings&gt;
     *   &lt;line-length&gt;100&lt;/line-length&gt;
     *   &lt;target-version&gt;"py311"&lt;/target-version&gt;
     *   &lt;format.quote-style&gt;"double"&lt;/format.quote-style&gt;
     *   &lt;lint.pydocstyle.convention&gt;"google"&lt;/lint.pydocstyle.convention&gt;
     * &lt;/ruffSettings&gt;
     * </pre>
     *
     * A setting named here overrides the project's own value for it; every other setting stays the project's.
     * Set it in a parent POM to configure many projects at once: a child POM inherits each entry and can
     * override single entries.
     */
    @Parameter
    private Map<String, String> ruffSettings;

    // Runs ruff with the given subcommand and its arguments, adding the build directory exclusion and the
    // ruffSettings as --config overrides, which ruff applies over the project's own configuration.
    protected void runRuff(String name, String failure, List<String> subcommand, List<String> args)
            throws MojoExecutionException, MojoFailureException {
        List<String> command = new ArrayList<>(subcommand);
        command.add("--config");
        command.add(ruffExcludeBuildDir());
        for (Map.Entry<String, String> setting : new TreeMap<>(ruffSettings == null ? Map.of() : ruffSettings)
                .entrySet()) {
            String key = setting.getKey().trim();
            String value = setting.getValue() == null ? "" : setting.getValue().trim();
            if (value.isEmpty()) {
                throw new MojoExecutionException("The ruffSettings entry <" + key + "> has no value. Give it a"
                        + " TOML value, such as 100, \"py311\" or [\"E\", \"F\"].");
            }
            command.add("--config");
            command.add(key + " = " + value);
        }
        command.addAll(args);
        Path ruff = installTool(getToolsVenvPath(), "ruff", ruffVersion);
        runCheck(name, failure, ruff, command);
    }
}
