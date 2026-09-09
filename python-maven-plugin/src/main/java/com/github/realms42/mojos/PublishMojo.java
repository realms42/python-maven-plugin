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

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.crypto.DefaultSettingsDecryptionRequest;
import org.apache.maven.settings.crypto.SettingsDecrypter;
import org.apache.maven.settings.crypto.SettingsDecryptionResult;

import com.github.realms42.mojos.base.AbstractPythonMojo;

/**
 * Uploads the built distributions to a Python package index with twine.
 *
 * <p>Deliberately not part of the python lifecycle. Publishing is a policy
 * decision, so a project opts in with an execution — typically once in a parent
 * POM — rather than every project inheriting it and having to opt out.
 *
 * <pre>{@code
 * <execution>
 *   <goals><goal>publish</goal></goals>
 *   <configuration>
 *     <serverId>internal-pypi</serverId>
 *     <repositoryUrl>https://artifactory.example.com/artifactory/api/pypi/pypi-release-local/</repositoryUrl>
 *     <snapshotRepositoryUrl>https://artifactory.example.com/artifactory/api/pypi/pypi-snapshot-local/</snapshotRepositoryUrl>
 *   </configuration>
 * </execution>
 * }</pre>
 *
 * <p>Releases and snapshots go to separate repositories, mirroring Maven's own
 * split between {@code <repository>} and {@code <snapshotRepository>}. An index
 * treats a version as immutable, so a snapshot — which keeps the same PEP 440
 * version on every build — can only be republished where overwriting is
 * allowed. Keeping the two apart means a snapshot can never land in the
 * repository that is supposed to be immutable.
 *
 * <p>Credentials come from the matching {@code <server>} in settings.xml and are
 * passed to twine through the environment, never as arguments.
 */
@Mojo(name = "publish", defaultPhase = LifecyclePhase.DEPLOY)
public class PublishMojo extends AbstractPythonMojo {

    @Parameter(defaultValue = "${project.version}", readonly = true)
    private String version;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    @Component
    private SettingsDecrypter settingsDecrypter;

    /**
     * Index that release versions are uploaded to, for example
     * {@code https://artifactory.example.com/artifactory/api/pypi/pypi-release-local/}.
     * Required to publish a release: without it twine would default to public
     * PyPI, which is not a mistake worth making by accident.
     *
     * <p>Note this has to be a concrete repository. A virtual repository can
     * aggregate several for resolution, but is not a valid upload target.
     */
    @Parameter(property = "python.publish.url")
    private String repositoryUrl;

    /**
     * Index that SNAPSHOT versions are uploaded to, which should be one that
     * permits redeployment. Leave it unset to publish releases only: a snapshot
     * build then does nothing rather than falling back to the release
     * repository.
     */
    @Parameter(property = "python.publish.snapshotUrl")
    private String snapshotRepositoryUrl;

    /** id of the {@code <server>} in settings.xml holding the credentials. */
    @Parameter(property = "python.publish.serverId")
    private String serverId;

    @Parameter(property = "python.publish.skip", defaultValue = "false")
    private boolean skipPublish;

    /**
     * Pass --skip-existing, so re-publishing an existing version is not an error.
     * Off by default because twine only accepts it for indexes it recognises; against
     * anything else, Artifactory included, it refuses to run at all:
     *
     * <pre>UnsupportedConfiguration: The configured repository '...' does not have
     * support for the following features: --skip-existing</pre>
     */
    @Parameter(property = "python.publish.skipExisting", defaultValue = "false")
    private boolean skipExisting;

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
            throw new MojoExecutionException("No distributions to publish in " + getBuildPath()
                    + ". Run the build goal first.");
        }

        runInVenv(python, "-m", "pip", "install", "--quiet", "--disable-pip-version-check", "twine");

        List<String> files = distributions.stream().map(Path::toString).collect(Collectors.toList());
        for (Path distribution : distributions) {
            getLog().info("Publishing: " + distribution.getFileName());
        }

        runInVenv(command("twine", "check", files));
        runInVenv(command("twine", "upload", uploadOptions(target, files)), credentials());
        getLog().info("Published " + distributions.size() + " distribution(s) to " + target);
    }

    /**
     * The index this version belongs in, or null if there is nowhere to publish
     * it. A snapshot with no snapshot repository configured is skipped rather
     * than diverted to the release repository.
     */
    private String targetRepository() throws MojoExecutionException {
        if (isSnapshot()) {
            if (isBlank(snapshotRepositoryUrl)) {
                getLog().info("Not publishing " + version + ": no snapshotRepositoryUrl is set,"
                        + " and snapshots are never sent to the release repository.");
                return null;
            }
            return snapshotRepositoryUrl;
        }
        if (isBlank(repositoryUrl)) {
            throw new MojoExecutionException("repositoryUrl is required to publish " + version
                    + ", so that a publish cannot reach public PyPI by defaulting there.");
        }
        return repositoryUrl;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean isSnapshot() {
        return version != null && version.toUpperCase().endsWith("-SNAPSHOT");
    }

    /** Everything the build produced: the sdist and every wheel. */
    private List<Path> distributions() throws MojoExecutionException {
        Path buildPath = getBuildPath();
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
        options.add("--repository-url");
        options.add(target);
        if (skipExisting) {
            options.add("--skip-existing");
        }
        options.addAll(files);
        return options;
    }

    private static List<String> command(String program, String subcommand, List<String> rest) {
        List<String> command = new ArrayList<>();
        command.add(program);
        command.add(subcommand);
        command.addAll(rest);
        return command;
    }

    /**
     * TWINE_USERNAME and TWINE_PASSWORD from the settings.xml server, decrypted
     * the same way Maven decrypts its own repository credentials. Returned as
     * environment rather than arguments so the password never reaches the
     * command line, which is echoed on failure and readable in the process table.
     */
    private Map<String, String> credentials() throws MojoExecutionException {
        Map<String, String> env = new HashMap<>();
        if (serverId == null || serverId.trim().isEmpty()) {
            getLog().info("No serverId configured; leaving twine to find credentials itself"
                    + " (TWINE_* in the environment, or ~/.pypirc).");
            return env;
        }
        Server server = session.getSettings().getServer(serverId);
        if (server == null) {
            throw new MojoExecutionException("No <server> with id '" + serverId + "' in settings.xml.");
        }
        SettingsDecryptionResult decrypted = settingsDecrypter.decrypt(
                new DefaultSettingsDecryptionRequest(server));
        Server resolved = decrypted.getServer() != null ? decrypted.getServer() : server;
        if (resolved.getUsername() != null) {
            env.put("TWINE_USERNAME", resolved.getUsername());
        }
        if (resolved.getPassword() != null) {
            env.put("TWINE_PASSWORD", resolved.getPassword());
        }
        getLog().info("Using credentials from server '" + serverId + "'.");
        return env;
    }
}
