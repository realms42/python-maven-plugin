package com.github.realms42.mojos.base;

import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;

public abstract class AbstractVenvCommandMojo extends AbstractPythonMojo {

    protected abstract List<String> command();

    protected abstract String label();

    protected boolean isSkipped() {
        return false;
    }

    @Override
    public final void execute() throws MojoExecutionException {
        if (isSkipped()) {
            getLog().info(label() + " skipped.");
            return;
        }
        runInVenv(command());
        getLog().info(label() + " completed.");
    }
}
