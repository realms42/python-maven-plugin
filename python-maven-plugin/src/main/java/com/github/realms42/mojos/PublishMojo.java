package com.github.realms42.mojos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.crypto.DefaultSettingsDecryptionRequest;
import org.apache.maven.settings.crypto.SettingsDecrypter;
import org.apache.maven.settings.crypto.SettingsDecryptionResult;

@Mojo(name = "publish", defaultPhase = LifecyclePhase.DEPLOY)
public class PublishMojo extends BaseMojo {

    @Parameter(defaultValue = "${project.version}", readonly = true)
    private String version;

    @Parameter(defaultValue = "${settings}", readonly = true)
    private Settings settings;

    @Component
    private SettingsDecrypter settingsDecrypter;

    @Parameter(property = "python.publish.url")
    private String repositoryUrl;

    @Parameter(property = "python.publish.snapshotUrl")
    private String snapshotRepositoryUrl;

    @Parameter(property = "python.publish.serverId")
    private String serverId;

    @Parameter(property = "python.publish.skip", defaultValue = "false")
    private boolean skipPublish;

    @Override
    public void execute() throws MojoExecutionException {
        if (skipPublish) {
            getLog().info("Publishing skipped.");
            return;
        }
        String target = targetRepository();
        if (target == null) {
            return;
        }

        List<Path> distributions = distributions();
        if (distributions.isEmpty()) {
            throw new MojoExecutionException("No distributions to publish in " + getPythonBuildPath()
                    + ". Run the build goal first.");
        }

        List<String> files = distributions.stream().map(Path::toString).collect(Collectors.toList());
        for (Path distribution : distributions) {
            getLog().info("Publishing: " + distribution.getFileName());
        }
        Map<String, String> credentials = credentials();
        List<String> args = new ArrayList<>();
        args.add("publish");
        args.add("--dry-run");
        args.addAll(uploadOptions(target, files));
        run("uv", args, credentials);
        args.clear();
        args.add("publish");
        args.addAll(uploadOptions(target, files));
        run("uv", args, credentials);
        getLog().info("Published " + distributions.size() + " distribution(s) to " + target);
    }

    private String targetRepository() throws MojoExecutionException {
        if (isSnapshot()) {
            if (snapshotRepositoryUrl == null || snapshotRepositoryUrl.isBlank()) {
                getLog().warn("Not publishing " + version + ": no snapshotRepositoryUrl is set,"
                        + " and snapshots are never sent to the release repository.");
                return null;
            }
            return snapshotRepositoryUrl;
        }
        if (repositoryUrl == null || repositoryUrl.isBlank()) {
            throw new MojoExecutionException("repositoryUrl is required to publish " + version
                    + ", so that a publish cannot reach public PyPI by defaulting there.");
        }
        return repositoryUrl;
    }

    private boolean isSnapshot() {
        return version != null && version.toUpperCase().endsWith("-SNAPSHOT");
    }

    private List<Path> distributions() throws MojoExecutionException {
        Path buildPath = getPythonBuildPath();
        if (!Files.isDirectory(buildPath)) {
            return new ArrayList<>();
        }
        try (Stream<Path> entries = Files.list(buildPath)) {
            return entries.filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.endsWith(".whl") || name.endsWith(".tar.gz");
                    })
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to list " + buildPath + ": " + e.getMessage());
        }
    }

    private List<String> uploadOptions(String target, List<String> files) {
        List<String> options = new ArrayList<>();
        options.add("--publish-url");
        options.add(target);
        options.addAll(files);
        return options;
    }

    private Map<String, String> credentials() throws MojoExecutionException {
        Map<String, String> env = new HashMap<>();
        if (serverId == null || serverId.isBlank()) {
            getLog().debug("No serverId configured; leaving uv to find credentials itself"
                    + " (UV_PUBLISH_* in the environment, or trusted publishing).");
            return env;
        }
        Server server = settings.getServer(serverId);
        if (server == null) {
            throw new MojoExecutionException("No <server> with id '" + serverId + "' in settings.xml.");
        }

        SettingsDecryptionResult decrypted = settingsDecrypter.decrypt(
                new DefaultSettingsDecryptionRequest(server));

        if (!decrypted.getProblems().isEmpty()) {
            throw new MojoExecutionException(
                    "Could not decrypt server '" + serverId + "'" + decrypted.getProblems());
        }

        Server resolved = decrypted.getServer() != null ? decrypted.getServer() : server;
        if (resolved.getUsername() != null) {
            env.put("UV_PUBLISH_USERNAME", resolved.getUsername());
        }
        if (resolved.getPassword() != null) {
            env.put("UV_PUBLISH_PASSWORD", resolved.getPassword());
        }
        getLog().debug("Using credentials from server '" + serverId + "'.");
        return env;
    }
}
