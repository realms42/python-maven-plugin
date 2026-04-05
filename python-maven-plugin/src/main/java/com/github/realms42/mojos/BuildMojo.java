package com.github.realms42.mojos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

import com.github.realms42.util.CommandRunnerUtil;

@Mojo(name = "build", defaultPhase = LifecyclePhase.COMPILE)
public class BuildMojo extends BaseMojo {

    @Override
    public void execute() throws MojoExecutionException {
        List<String> cmd = new ArrayList<>(
                Arrays.asList(getVenvBin("python3").toString(), "-m", "build", "--outdir", getBuildPath().toString()));
        CommandRunnerUtil.run(getWorkPath(), cmd);
    }
}
