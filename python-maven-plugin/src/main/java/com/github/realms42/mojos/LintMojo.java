package com.github.realms42.mojos;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Lints the project with {@code ruff check}, using the project's own ruff configuration plus the
 * {@code enforcedRules}. Runs the plugin's pinned ruff ({@code ruffVersion}), not the project's.
 * Never fixes anything: it reports violations and fails the build.
 */
@Mojo(name = "lint", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public class LintMojo extends BaseMojo {

    /** Skips linting. */
    @Parameter(property = "python.lint.skip", defaultValue = "false")
    private boolean skip;

    /**
     * The exact ruff version the plugin runs, from its own environment under {@code target/}. A project's own
     * ruff dependency is for running ruff itself, outside Maven, and isn't used here.
     */
    @Parameter(property = "python.ruff.version", defaultValue = RUFF_VERSION)
    private String ruffVersion;

    /**
     * The base ruleset every project is linted with, as ruff rule selectors such as {@code E} or {@code B006}.
     * Projects add to it with their own ruff configuration ({@code extend-select} in {@code pyproject.toml} or
     * {@code ruff.toml}), which ruff also reads when run outside Maven. A project's {@code select} and
     * {@code ignore} can't switch these rules off; {@code per-file-ignores} and {@code # noqa} still work.
     * Set it in a parent POM to apply one base to many projects, or leave it empty to lint with the project's
     * own configuration alone.
     */
    // Passed as --extend-select, which ruff applies over a project's `select` and `ignore`. The default is
    // declared on the annotation because Maven injects an empty list over a field initializer when the
    // property is unset.
    @Parameter(property = "python.lint.enforcedRules", defaultValue = "E,W,F,I,B,UP")
    private List<String> enforcedRules;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Linting is skipped.");
            return;
        }
        List<String> args = new ArrayList<>(Arrays.asList("check", "--config", ruffExcludeBuildDir()));
        List<String> rules = enforcedRules == null ? List.of() : enforcedRules.stream()
                .map(String::trim)
                .filter(rule -> !rule.isEmpty())
                .collect(Collectors.toList());
        if (!rules.isEmpty()) {
            args.add("--extend-select");
            args.add(String.join(",", rules));
        }
        Path ruff = installTool(getToolsVenvPath(), "ruff", ruffVersion);
        runCheck("ruff check", "ruff found lint violations. See its output above.", ruff, args);
    }
}
