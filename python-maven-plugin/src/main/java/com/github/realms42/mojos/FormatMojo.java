package com.github.realms42.mojos;

import java.util.Arrays;
import java.util.List;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "format", defaultPhase = LifecyclePhase.TEST)
public class FormatMojo extends VenvCommandMojo {

    @Parameter(property = "python.format.skip", defaultValue = "false")
    private boolean skipFormat;

    @Override
    protected List<String> command() {
        return Arrays.asList("ruff", "format", "--check", ".");
    }

    @Override
    protected String label() {
        return "Format check";
    }

    @Override
    protected boolean isSkipped() {
        return skipFormat;
    }
}
