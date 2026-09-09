package com.github.realms42.mojos;

import java.util.Arrays;
import java.util.List;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

import com.github.realms42.mojos.base.AbstractVenvCommandMojo;

@Mojo(name = "build", defaultPhase = LifecyclePhase.COMPILE)
public class BuildMojo extends AbstractVenvCommandMojo {

    @Override
    protected List<String> command() {
        return Arrays.asList(python, "-m", "build", "--outdir", getBuildPath().toString());
    }

    @Override
    protected String label() {
        return "Build";
    }
}
