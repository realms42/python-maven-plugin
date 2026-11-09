package io.github.realms42.mojos;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/** Lints the project with {@code ruff check}, without fixing anything. */
@Mojo(name = "lint", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public class LintMojo extends RuffMojo {

    /** Skips linting. */
    @Parameter(property = "python.lint.skip", defaultValue = "false")
    private boolean skip;

    /** Rule selectors always checked, on top of the project's own rules. */
    // The default must be on the annotation: Maven injects an empty list over a field initializer.
    @Parameter(property = "python.lint.enforcedRules", defaultValue = "E,W,F,I,B,UP")
    private List<String> enforcedRules;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Linting is skipped.");
            return;
        }
        List<String> args = new ArrayList<>();
        List<String> rules = enforcedRules == null ? List.of() : enforcedRules.stream()
                .map(String::trim)
                .filter(rule -> !rule.isEmpty())
                .collect(Collectors.toList());
        if (!rules.isEmpty()) {
            args.add("--extend-select");
            args.add(String.join(",", rules));
        }
        runRuff("ruff check", "ruff found lint violations. See its output above.", List.of("check"), args);
    }
}
