package io.github.realms42.mojos;

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

/** Uploads the built distributions to a Python package index with {@code uv publish}. */
@Mojo(name = "publish", defaultPhase = LifecyclePhase.DEPLOY, threadSafe = true)
public class PublishMojo extends BaseMojo {

    @Parameter(defaultValue = "${settings}", readonly = true)
    private Settings settings;

    @Component
    private SettingsDecrypter settingsDecrypter;

    /** The index for release versions: a {@code url}, and an {@code id} naming a settings.xml server. */
    @Parameter
    private PublishRepository releaseRepository;

    /** The index for -SNAPSHOT versions, configured like {@code releaseRepository}. */
    @Parameter
    private PublishRepository snapshotRepository;

    // Read as default values, not with `property`, which would warn about configuring a read-only parameter.
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

        List<Path> distributions = builtDistributions();
        if (distributions.isEmpty()) {
            throw new MojoExecutionException("No distributions to publish in " + getPythonBuildPath()
                    + ". Run the build goal first.");
        }

        for (Path distribution : distributions) {
            getLog().info("Publishing: " + distribution.getFileName());
        }
        Map<String, String> credentials = credentials(repository.getId(), snapshot ? "snapshot" : "release");
        List<String> upload = new ArrayList<>(List.of("--publish-url", target));
        distributions.forEach(d -> upload.add(d.toString()));
        run("uv", concat(List.of("publish", "--dry-run"), upload), credentials);
        run("uv", concat(List.of("publish"), upload), credentials);
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
        return repository;
    }

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

    private static List<String> concat(List<String> first, List<String> second) {
        return Stream.concat(first.stream(), second.stream()).collect(Collectors.toList());
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
