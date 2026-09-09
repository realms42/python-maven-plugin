package com.github.realms42.mojos;

import java.util.Arrays;
import java.util.List;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import com.github.realms42.mojos.base.AbstractVenvCommandMojo;

@Mojo(name = "lint", defaultPhase = LifecyclePhase.TEST)
public class LintMojo extends AbstractVenvCommandMojo {

    @Parameter(property = "python.lint.skip", defaultValue = "false")
    private boolean skipLint;

    @Override
    protected List<String> command() {
        return Arrays.asList("ruff", "check", ".");
    }

    @Override
    protected String label() {
        return "Linting";
    }

    @Override
    protected boolean isSkipped() {
        return skipLint;
    }
}
