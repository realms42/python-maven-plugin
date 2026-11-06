package com.github.realms42.mojos;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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

/**
 * Uploads the built distributions to a private Python package index with {@code uv publish}. Releases and
 * snapshots go to separate repositories. There is no default, and public PyPI and TestPyPI are always refused, so
 * nothing is ever published to them. Every file is validated with a dry run before anything is uploaded. Not
 * bound to the lifecycle by default.
 */
@Mojo(name = "publish", defaultPhase = LifecyclePhase.DEPLOY, threadSafe = true)
public class PublishMojo extends BaseMojo {

    @Parameter(defaultValue = "${project.version}", readonly = true)
    private String version;

    @Parameter(defaultValue = "${settings}", readonly = true)
    private Settings settings;

    @Component
    private SettingsDecrypter settingsDecrypter;

    /**
     * The package index that release versions are published to, with an {@code id} naming the
     * {@code settings.xml} {@code <server>} that holds its credentials, and a {@code url}. Each value falls back to
     * the {@code python.publish.releaseRepository.id} and {@code .url} properties. Without an id, uv finds
     * credentials itself ({@code UV_PUBLISH_*}, or trusted publishing).
     */
    @Parameter
    private PublishRepository releaseRepository;

    /**
     * The package index that {@code -SNAPSHOT} versions are published to, configured like
     * {@code releaseRepository}. Each value falls back to the {@code python.publish.snapshotRepository.id} and
     * {@code .url} properties.
     */
    @Parameter
    private PublishRepository snapshotRepository;

    // The same repositories as properties, so a parent POM, a settings.xml profile or -D can set them.
    // A <releaseRepository> or <snapshotRepository> block wins value by value. These read the properties
    // as default values rather than binding them with `property`, which would make Maven warn that a
    // read-only parameter is configured whenever one is set.
    @Parameter(defaultValue = "${python.publish.releaseRepository.id}", readonly = true)
    private String releaseRepositoryId;

    @Parameter(defaultValue = "${python.publish.releaseRepository.url}", readonly = true)
    private String releaseRepositoryUrl;

    @Parameter(defaultValue = "${python.publish.snapshotRepository.id}", readonly = true)
    private String snapshotRepositoryId;

    @Parameter(defaultValue = "${python.publish.snapshotRepository.url}", readonly = true)
    private String snapshotRepositoryUrl;

    /** Skips publishing. */
    @Parameter(property = "python.publish.skip", defaultValue = "false")
    private boolean skipPublish;

    /** Skips publishing release versions. */
    @Parameter(property = "python.publish.skipReleases", defaultValue = "false")
    private boolean skipReleases;

    /** Skips publishing {@code -SNAPSHOT} versions. */
    @Parameter(property = "python.publish.skipSnapshots", defaultValue = "false")
    private boolean skipSnapshots;

    @Override
    public void execute() throws MojoExecutionException {
        if (skipPublish) {
            getLog().info("Publishing skipped.");
            return;
        }
        boolean snapshot = isSnapshot();
        if (snapshot ? skipSnapshots : skipReleases) {
            getLog().info("Publishing " + (snapshot ? "snapshots" : "releases") + " skipped.");
            return;
        }
        if (offline) {
            throw new MojoExecutionException("Can't publish in offline mode. Run without -o, or skip publishing"
                    + " with -Dpython.publish.skip.");
        }
        PublishRepository repository = targetRepository(snapshot);
        String target = repository.getUrl();

        List<Path> distributions = distributions();
        if (distributions.isEmpty()) {
            throw new MojoExecutionException("No distributions to publish in " + getPythonBuildPath()
                    + ". Run the build goal first.");
        }

        List<String> files = distributions.stream().map(Path::toString).collect(Collectors.toList());
        for (Path distribution : distributions) {
            getLog().info("Publishing: " + distribution.getFileName());
        }
        Map<String, String> credentials = credentials(repository.getId(), snapshot ? "snapshot" : "release");
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

    private PublishRepository targetRepository(boolean snapshot) throws MojoExecutionException {
        PublishRepository repository = snapshot
                ? merge(snapshotRepository, snapshotRepositoryId, snapshotRepositoryUrl)
                : merge(releaseRepository, releaseRepositoryId, releaseRepositoryUrl);
        if (repository.getUrl() == null) {
            String kind = snapshot ? "snapshot" : "release";
            throw new MojoExecutionException("No " + kind + " repository URL to publish " + version + " to."
                    + " Set <" + kind + "Repository><url> or the python.publish." + kind + "Repository.url property,"
                    + " or skip with -Dpython.publish.skip" + (snapshot ? "Snapshots" : "Releases") + "."
                    + " There is no default, so a publish can't reach public PyPI by accident.");
        }
        refusePublicIndexes(repository.getUrl());
        return repository;
    }

    // Packages are only ever published to private package indexes. Public PyPI and TestPyPI are refused
    // outright, whatever the configuration says, before uv runs at all.
    static void refusePublicIndexes(String url) throws MojoExecutionException {
        String host;
        try {
            host = new URI(url.trim()).getHost();
        } catch (URISyntaxException e) {
            throw new MojoExecutionException("The repository URL " + url + " is not a valid URL: " + e.getMessage());
        }
        if (host == null) {
            throw new MojoExecutionException("The repository URL " + url + " has no host.");
        }
        host = host.toLowerCase(Locale.ROOT);
        if (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        for (String publicIndex : PUBLIC_INDEXES) {
            if (host.equals(publicIndex) || host.endsWith("." + publicIndex)) {
                throw new MojoExecutionException("Refusing to publish to " + url + ": it is a public package index ("
                        + publicIndex + "). This plugin only publishes to private package indexes.");
            }
        }
    }

    // pypi.org covers upload.pypi.org and test.pypi.org; pythonhosted.org serves PyPI's files.
    private static final List<String> PUBLIC_INDEXES = List.of("pypi.org", "pythonhosted.org", "pypi.python.org");

    private static PublishRepository merge(PublishRepository block, String propertyId, String propertyUrl) {
        return new PublishRepository(
                valueOf(block == null ? null : block.getId(), propertyId),
                valueOf(block == null ? null : block.getUrl(), propertyUrl));
    }

    private static String valueOf(String configured, String property) {
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return property == null || property.isBlank() ? null : property;
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

    private Map<String, String> credentials(String serverId, String kind) throws MojoExecutionException {
        Map<String, String> env = new HashMap<>();
        if (serverId == null) {
            getLog().debug("The " + kind + " repository has no id; leaving uv to find credentials itself"
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
