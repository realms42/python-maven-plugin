package com.github.realms42.mojos;

import java.util.Arrays;
import java.util.List;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import com.github.realms42.mojos.base.AbstractVenvCommandMojo;

@Mojo(name = "test", defaultPhase = LifecyclePhase.TEST)
public class TestMojo extends AbstractVenvCommandMojo {

    @Parameter(property = "python.test.skip", defaultValue = "false")
    private boolean skipTests;

    @Override
    protected List<String> command() {
        return Arrays.asList("pytest");
    }

    @Override
    protected String label() {
        return "Tests";
    }

    @Override
    protected boolean isSkipped() {
        return skipTests;
    }
}
