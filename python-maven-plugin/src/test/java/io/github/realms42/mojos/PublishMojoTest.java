package io.github.realms42.mojos;

import static io.github.realms42.mojos.MojoTesting.configure;
import static io.github.realms42.mojos.MojoTesting.log;
import static io.github.realms42.mojos.MojoTesting.set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.settings.Proxy;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.DefaultSettingsProblem;
import org.apache.maven.settings.building.SettingsProblem;
import org.apache.maven.settings.crypto.SettingsDecrypter;
import org.apache.maven.settings.crypto.SettingsDecryptionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.realms42.mojos.MojoTesting.Commands;

class PublishMojoTest {

    private static final String RELEASES = "https://pypi.example.com/releases/";
    private static final String SNAPSHOTS = "https://pypi.example.com/snapshots/";
    private static final Map<String, String> ALICE =
            Map.of("UV_PUBLISH_USERNAME", "alice", "UV_PUBLISH_PASSWORD", "alice-secret");
    private static final Map<String, String> BOB =
            Map.of("UV_PUBLISH_USERNAME", "bob", "UV_PUBLISH_PASSWORD", "bob-secret");

    @TempDir
    Path project;

    private final Commands commands = new Commands();
    private final Settings settings = new Settings();
    private SettingsDecrypter decrypter = request -> {
        Server decrypted = request.getServers().get(0).clone();
        decrypted.setPassword(decrypted.getUsername() + "-secret");
        return result(decrypted);
    };
    private Path output;
    private String sdist;
    private String wheel;

    @BeforeEach
    void setUp() throws IOException {
        output = Files.createDirectories(project.resolve("target/python-build"));
        sdist = Files.writeString(output.resolve("example-1.0.0.tar.gz"), "sdist").toString();
        wheel = Files.writeString(output.resolve("example-1.0.0-py3-none-any.whl"), "wheel").toString();
        Files.writeString(output.resolve(".gitignore"), "*");
        settings.addServer(server("releases", "alice", "{encrypted}"));
        settings.addServer(server("snapshots", "bob", "{encrypted}"));
    }

    private PublishMojo mojo(String version) {
        PublishMojo mojo = configure(new PublishMojo() {
            @Override
            protected int exec(String cmd, List<String> args, Map<String, String> env) {
                return commands.exec(cmd, args, env);
            }
        }, project, version);
        set(mojo, "version", version);
        set(mojo, "settings", settings);
        set(mojo, "settingsDecrypter", decrypter);
        return mojo;
    }

    private PublishMojo withBothRepositories(String version) {
        PublishMojo mojo = mojo(version);
        set(mojo, "releaseRepository", new PublishRepository("releases", RELEASES));
        set(mojo, "snapshotRepository", new PublishRepository("snapshots", SNAPSHOTS));
        return mojo;
    }

    @Test
    void snapshotIsCheckedThenPublishedToTheSnapshotRepository() throws Exception {
        withBothRepositories("1.0.0-SNAPSHOT").execute();

        assertEquals(List.of(
                List.of("uv", "publish", "--dry-run", "--publish-url", SNAPSHOTS, wheel, sdist),
                List.of("uv", "publish", "--publish-url", SNAPSHOTS, wheel, sdist)),
                commands.commands);
        assertEquals(List.of(BOB, BOB), commands.environments);
    }

    @Test
    void releaseIsCheckedThenPublishedToTheReleaseRepository() throws Exception {
        withBothRepositories("1.0.0").execute();

        assertEquals(List.of(
                List.of("uv", "publish", "--dry-run", "--publish-url", RELEASES, wheel, sdist),
                List.of("uv", "publish", "--publish-url", RELEASES, wheel, sdist)),
                commands.commands);
        assertEquals(List.of(ALICE, ALICE), commands.environments);
    }

    @Test
    void repositoriesCanComeFromProperties() throws Exception {
        PublishMojo mojo = mojo("1.0.0-SNAPSHOT");
        set(mojo, "snapshotRepositoryId", "snapshots");
        set(mojo, "snapshotRepositoryUrl", SNAPSHOTS);

        mojo.execute();

        assertEquals(List.of("uv", "publish", "--publish-url", SNAPSHOTS, wheel, sdist), commands.commands.get(1));
        assertEquals(List.of(BOB, BOB), commands.environments);
    }

    @Test
    void theBlockWinsOverThePropertiesValueByValue() throws Exception {
        PublishMojo mojo = mojo("1.0.0");
        set(mojo, "releaseRepository", new PublishRepository(null, "https://block.example.com/"));
        set(mojo, "releaseRepositoryId", "releases");
        set(mojo, "releaseRepositoryUrl", "https://property.example.com/");

        mojo.execute();

        assertEquals(List.of("uv", "publish", "--publish-url", "https://block.example.com/", wheel, sdist),
                commands.commands.get(1));
        assertEquals(List.of(ALICE, ALICE), commands.environments);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void snapshotWithoutASnapshotUrlFails(String url) {
        PublishMojo mojo = withBothRepositories("1.0.0-SNAPSHOT");
        set(mojo, "snapshotRepository", new PublishRepository("snapshots", url));

        MojoExecutionException e = assertThrows(MojoExecutionException.class, mojo::execute);

        assertEquals("No snapshot repository URL to publish 1.0.0-SNAPSHOT to."
                + " Set <snapshotRepository><url> or the python.publish.snapshotRepository.url property,"
                + " or skip with -Dpython.publish.skipSnapshots."
                + " There is no default, so a publish can't reach public PyPI by accident.", e.getMessage());
        assertTrue(commands.commands.isEmpty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void releaseWithoutAReleaseUrlFails(String url) {
        PublishMojo mojo = withBothRepositories("1.0.0");
        set(mojo, "releaseRepository", new PublishRepository("releases", url));

        MojoExecutionException e = assertThrows(MojoExecutionException.class, mojo::execute);

        assertEquals("No release repository URL to publish 1.0.0 to."
                + " Set <releaseRepository><url> or the python.publish.releaseRepository.url property,"
                + " or skip with -Dpython.publish.skipReleases."
                + " There is no default, so a publish can't reach public PyPI by accident.", e.getMessage());
        assertTrue(commands.commands.isEmpty());
    }

    @Test
    void skipSnapshotsSkipsOnlySnapshotsAndNeedsNoSnapshotUrl() throws Exception {
        PublishMojo snapshot = mojo("1.0.0-SNAPSHOT");
        set(snapshot, "skipSnapshots", true);

        snapshot.execute();

        assertTrue(commands.commands.isEmpty());
        assertEquals(List.of("Publishing snapshots skipped."), log(snapshot).info);

        PublishMojo release = withBothRepositories("1.0.0");
        set(release, "skipSnapshots", true);

        release.execute();

        assertEquals(2, commands.commands.size());
    }

    @Test
    void skipReleasesSkipsOnlyReleasesAndNeedsNoReleaseUrl() throws Exception {
        PublishMojo release = mojo("1.0.0");
        set(release, "skipReleases", true);

        release.execute();

        assertTrue(commands.commands.isEmpty());
        assertEquals(List.of("Publishing releases skipped."), log(release).info);

        PublishMojo snapshot = withBothRepositories("1.0.0-SNAPSHOT");
        set(snapshot, "skipReleases", true);

        snapshot.execute();

        assertEquals(2, commands.commands.size());
    }

    @Test
    void skipFlagSkipsPublishing() throws Exception {
        PublishMojo mojo = mojo("1.0.0");
        set(mojo, "skipPublish", true);

        mojo.execute();

        assertTrue(commands.commands.isEmpty());
        assertEquals(List.of("Publishing skipped."), log(mojo).info);
    }

    @Test
    void publishesToTheConfiguredIndexWhateverItIs() throws Exception {
        PublishMojo mojo = mojo("1.0.0");
        set(mojo, "releaseRepositoryUrl", "https://test.pypi.org/legacy/");

        mojo.execute();

        assertEquals(List.of(
                List.of("uv", "publish", "--dry-run", "--publish-url", "https://test.pypi.org/legacy/", wheel, sdist),
                List.of("uv", "publish", "--publish-url", "https://test.pypi.org/legacy/", wheel, sdist)),
                commands.commands);
    }

    @Test
    void refusesToPublishInOfflineModeWithoutRunningUv() {
        PublishMojo mojo = withBothRepositories("1.0.0");
        mojo.offline = true;

        MojoExecutionException e = assertThrows(MojoExecutionException.class, mojo::execute);

        assertTrue(e.getMessage().startsWith("Can't publish in offline mode."), e.getMessage());
        assertTrue(commands.commands.isEmpty());
    }

    @Test
    void skipFlagSkipsPublishingEvenOffline() throws Exception {
        PublishMojo mojo = mojo("1.0.0");
        set(mojo, "skipPublish", true);
        mojo.offline = true;

        mojo.execute();

        assertTrue(commands.commands.isEmpty());
    }

    @Test
    void publishesOnlyWhatTheBuildWroteToPythonBuild() throws Exception {
        Files.delete(Path.of(sdist));
        Files.delete(Path.of(wheel));
        Files.writeString(project.resolve("target/example-0.9.0.tar.gz"), "not from this build");

        MojoExecutionException e = assertThrows(MojoExecutionException.class,
                () -> withBothRepositories("1.0.0").execute());

        assertEquals("No distributions to publish in " + output + ". Run the build goal first.", e.getMessage());
        assertTrue(commands.commands.isEmpty());
    }

    @Test
    void nothingIsUploadedWhenTheCheckFails() {
        commands.exitingWith(2);

        assertThrows(MojoExecutionException.class, () -> withBothRepositories("1.0.0").execute());

        assertEquals(1, commands.commands.size());
        assertEquals("--dry-run", commands.commands.get(0).get(2));
    }

    @Test
    void withoutAnIdUvFindsItsOwnCredentials() throws Exception {
        PublishMojo mojo = mojo("1.0.0");
        set(mojo, "releaseRepository", new PublishRepository(null, RELEASES));

        mojo.execute();

        assertEquals(List.of(Map.of(), Map.of()), commands.environments);
        assertEquals(List.of("The release repository has no id; leaving uv to find credentials itself"
                + " (UV_PUBLISH_* in the environment, or trusted publishing)."), log(mojo).debug);
    }

    @Test
    void failsOnAnIdMissingFromSettings() {
        PublishMojo mojo = mojo("1.0.0");
        set(mojo, "releaseRepository", new PublishRepository("missing", RELEASES));

        MojoExecutionException e = assertThrows(MojoExecutionException.class, mojo::execute);

        assertEquals("No <server> with id 'missing' in settings.xml.", e.getMessage());
        assertTrue(commands.commands.isEmpty());
    }

    @Test
    void failsWhenTheServerCannotBeDecrypted() {
        decrypter = request -> result(null, new DefaultSettingsProblem(
                "Unable to decrypt", SettingsProblem.Severity.ERROR, "settings-security.xml", -1, -1, null));
        PublishMojo mojo = withBothRepositories("1.0.0");

        MojoExecutionException e = assertThrows(MojoExecutionException.class, mojo::execute);

        assertTrue(e.getMessage().startsWith("Could not decrypt server 'releases'"), e.getMessage());
        assertTrue(commands.commands.isEmpty());
    }

    @Test
    void logsWhatItPublishesAtInfo() throws Exception {
        PublishMojo mojo = withBothRepositories("1.0.0");

        mojo.execute();

        assertEquals(List.of(
                "Publishing: example-1.0.0-py3-none-any.whl",
                "Publishing: example-1.0.0.tar.gz",
                "Published 2 distribution(s) to " + RELEASES), log(mojo).info);
        assertEquals(List.of("Using credentials from server 'releases'."), log(mojo).debug);
        assertTrue(log(mojo).warnings.isEmpty());
    }

    private static Server server(String id, String username, String password) {
        Server server = new Server();
        server.setId(id);
        server.setUsername(username);
        server.setPassword(password);
        return server;
    }

    private static SettingsDecryptionResult result(Server server, SettingsProblem... problems) {
        return new SettingsDecryptionResult() {
            @Override
            public Server getServer() {
                return server;
            }

            @Override
            public List<Server> getServers() {
                return server == null ? List.of() : List.of(server);
            }

            @Override
            public Proxy getProxy() {
                return null;
            }

            @Override
            public List<Proxy> getProxies() {
                return List.of();
            }

            @Override
            public List<SettingsProblem> getProblems() {
                return List.of(problems);
            }
        };
    }
}
