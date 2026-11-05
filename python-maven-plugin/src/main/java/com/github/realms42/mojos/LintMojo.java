package com.github.realms42.mojos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "lint", defaultPhase = LifecyclePhase.VERIFY)
public class LintMojo extends BaseMojo {

    @Parameter(property = "python.lint.skip", defaultValue = "false")
    private boolean skip;

    // The base ruleset every project is linted with. Projects add to it with their own ruff configuration
    // (`extend-select` in pyproject.toml or ruff.toml), which ruff also reads when run outside Maven. It is passed
    // as --extend-select, which ruff applies over a project's `select` and `ignore`, so a project can't switch
    // these rules off; per-file-ignores and `# noqa` still work. A parent POM can set this for all its projects,
    // and an empty value lints with the project's own configuration alone. The default is declared on the
    // annotation because Maven injects an empty list over a field initializer when the property is unset.
    @Parameter(property = "python.lint.enforcedRules", defaultValue = "E,W,F,I,B,UP")
    private List<String> enforcedRules;

    @Override
    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("Linting is skipped.");
            return;
        }
        List<String> args = new ArrayList<>(Arrays.asList("check", "--config", ruffExcludeBuildDir()));
        List<String> rules = enforcedRules == null ? List.of() : enforcedRules.stream()
                .map(String::trim)
                .filter(rule -> !rule.isEmpty())
                .toList();
        if (!rules.isEmpty()) {
            args.add("--extend-select");
            args.add(String.join(",", rules));
        }
        run("uv", uvRun("ruff", args.toArray(String[]::new)), uvEnvironment());
    }
}
