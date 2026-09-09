package com.github.realms42.mojos;

import java.util.Collections;
import java.util.List;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "test", defaultPhase = LifecyclePhase.TEST)
public class TestMojo extends VenvCommandMojo {

    @Parameter(defaultValue = "pytest")
    private String tester;

    @Parameter(property = "python.test.skip", defaultValue = "false")
    private boolean skipTests;

    @Override
    protected List<String> command() {
        return Collections.singletonList(tester);
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
