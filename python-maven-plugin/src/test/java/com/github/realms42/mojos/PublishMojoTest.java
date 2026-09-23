package com.github.realms42.mojos;

import static com.github.realms42.mojos.MojoTesting.configure;
import static com.github.realms42.mojos.MojoTesting.log;
import static com.github.realms42.mojos.MojoTesting.set;
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

import com.github.realms42.mojos.MojoTesting.Commands;

class PublishMojoTest {

    private static final String RELEASES = "https://pypi.example.com/legacy/";
    private static final String SNAPSHOTS = "https://pypi.example.com/snapshots/";

    @TempDir
    Path project;

    private final Commands commands = new Commands();
    private final Settings settings = new Settings();
    private SettingsDecrypter decrypter = request -> {
        Server decrypted = request.getServers().get(0).clone();
        decrypted.setPassword("s3cret");
        return result(decrypted);
    };
    private Path output;
    private String sdist;
    private String wheel;

    @BeforeEach
    void builtDistributions() throws IOException {
        output = Files.createDirectories(project.resolve("target/python-build"));
        sdist = Files.writeString(output.resolve("example-1.0.0.tar.gz"), "sdist").toString();
        wheel = Files.writeString(output.resolve("example-1.0.0-py3-none-any.whl"), "wheel").toString();
        Files.writeString(output.resolve(".gitignore"), "*");
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
        set(mojo, "repositoryUrl", RELEASES);
        set(mojo, "snapshotRepositoryUrl", SNAPSHOTS);
        return mojo;
    }

    @Test
    void snapshotIsCheckedThenPublishedToTheSnapshotUrl() throws Exception {
        mojo("1.0.0-SNAPSHOT").execute();

        assertEquals(List.of(
                List.of("uv", "publish", "--dry-run", "--publish-url", SNAPSHOTS, wheel, sdist),
                List.of("uv", "publish", "--publish-url", SNAPSHOTS, wheel, sdist)),
                commands.commands);
    }

    @Test
    void releaseIsCheckedThenPublishedToTheReleaseUrl() throws Exception {
        mojo("1.0.0").execute();

        assertEquals(List.of(
                List.of("uv", "publish", "--dry-run", "--publish-url", RELEASES, wheel, sdist),
                List.of("uv", "publish", "--publish-url", RELEASES, wheel, sdist)),
                commands.commands);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void snapshotIsNotPublishedWithoutASnapshotUrl(String snapshotUrl) throws Exception {
        PublishMojo mojo = mojo("1.0.0-SNAPSHOT");
        set(mojo, "snapshotRepositoryUrl", snapshotUrl);

        mojo.execute();

        assertTrue(commands.commands.isEmpty());
        assertEquals(List.of("Not publishing 1.0.0-SNAPSHOT: no snapshotRepositoryUrl is set,"
                + " and snapshots are never sent to the release repository."), log(mojo).warnings);
        assertTrue(log(mojo).info.isEmpty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void releaseWithoutAReleaseUrlFails(String repositoryUrl) {
        PublishMojo mojo = mojo("1.0.0");
        set(mojo, "repositoryUrl", repositoryUrl);

        MojoExecutionException e = assertThrows(MojoExecutionException.class, mojo::execute);

        assertEquals("repositoryUrl is required to publish 1.0.0, so that a publish cannot reach public PyPI"
                + " by defaulting there.", e.getMessage());
        assertTrue(commands.commands.isEmpty());
    }

    @Test
    void publishesOnlyWhatTheBuildWroteToPythonBuild() throws Exception {
        Files.delete(Path.of(sdist));
        Files.delete(Path.of(wheel));
        Files.writeString(project.resolve("target/example-0.9.0.tar.gz"), "not from this build");

        MojoExecutionException e = assertThrows(MojoExecutionException.class, () -> mojo("1.0.0").execute());

        assertEquals("No distributions to publish in " + output + ". Run the build goal first.", e.getMessage());
        assertTrue(commands.commands.isEmpty());
    }

    @Test
    void nothingIsUploadedWhenTheCheckFails() {
        commands.exitingWith(2);

        assertThrows(MojoExecutionException.class, () -> mojo("1.0.0").execute());

        assertEquals(1, commands.commands.size());
        assertEquals("--dry-run", commands.commands.get(0).get(2));
    }

    @Test
    void credentialsComeFromTheDecryptedServer() throws Exception {
        settings.addServer(server("index", "alice", "{encrypted}"));
        PublishMojo mojo = mojo("1.0.0");
        set(mojo, "serverId", "index");

        mojo.execute();

        Map<String, String> credentials = Map.of("UV_PUBLISH_USERNAME", "alice", "UV_PUBLISH_PASSWORD", "s3cret");
        assertEquals(List.of(credentials, credentials), commands.environments);
        assertEquals(List.of("Using credentials from server 'index'."), log(mojo).debug);
    }

    @Test
    void withoutAServerIdUvFindsItsOwnCredentials() throws Exception {
        PublishMojo mojo = mojo("1.0.0");

        mojo.execute();

        assertEquals(List.of(Map.of(), Map.of()), commands.environments);
        assertEquals(List.of("No serverId configured; leaving uv to find credentials itself"
                + " (UV_PUBLISH_* in the environment, or trusted publishing)."), log(mojo).debug);
    }

    @Test
    void logsWhatItPublishesAtInfo() throws Exception {
        PublishMojo mojo = mojo("1.0.0");

        mojo.execute();

        assertEquals(List.of(
                "Publishing: example-1.0.0-py3-none-any.whl",
                "Publishing: example-1.0.0.tar.gz",
                "Published 2 distribution(s) to " + RELEASES), log(mojo).info);
        assertTrue(log(mojo).warnings.isEmpty());
    }

    @Test
    void failsOnAServerIdMissingFromSettings() {
        PublishMojo mojo = mojo("1.0.0");
        set(mojo, "serverId", "missing");

        MojoExecutionException e = assertThrows(MojoExecutionException.class, mojo::execute);

        assertEquals("No <server> with id 'missing' in settings.xml.", e.getMessage());
        assertTrue(commands.commands.isEmpty());
    }

    @Test
    void failsWhenTheServerCannotBeDecrypted() {
        settings.addServer(server("index", "alice", "{broken}"));
        decrypter = request -> result(null, new DefaultSettingsProblem(
                "Unable to decrypt", SettingsProblem.Severity.ERROR, "settings-security.xml", -1, -1, null));
        PublishMojo mojo = mojo("1.0.0");
        set(mojo, "serverId", "index");

        MojoExecutionException e = assertThrows(MojoExecutionException.class, mojo::execute);

        assertTrue(e.getMessage().startsWith("Could not decrypt server 'index'"), e.getMessage());
        assertTrue(commands.commands.isEmpty());
    }

    @Test
    void skipFlagSkipsPublishing() throws Exception {
        PublishMojo mojo = mojo("1.0.0");
        set(mojo, "skipPublish", true);

        mojo.execute();

        assertTrue(commands.commands.isEmpty());
        assertEquals(List.of("Publishing skipped."), log(mojo).info);
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
