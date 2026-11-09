package io.github.realms42.mojos;

import static io.github.realms42.mojos.MojoTesting.BUILD_START;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Optional;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class VersionsTest {

    @ParameterizedTest
    @CsvSource({
            "1.0.0, 1.0.0",
            "1.0.0-SNAPSHOT, 1.0.0.dev20261105152300",
            "1.0.0-alpha-1, 1.0.0a1",
            "1.0.0-alpha2, 1.0.0a2",
            "1.0.0-beta-3, 1.0.0b3",
            "1.0.0-rc-1, 1.0.0rc1",
            "2.0-RC2, 2.0rc2",
            "1.0.0.RELEASE, 1.0.0",
            "1.0.0.Final, 1.0.0",
            "1.0.0-GA, 1.0.0",
            "1.0.0-beta, 1.0.0b0",
            "1.0.0-cr-2, 1.0.0rc2",
            "1.0.0-a1, 1.0.0a1",
            "1.0.0-RC1-SNAPSHOT, 1.0.0rc1.dev20261105152300",
            "1.0.0-snapshot, 1.0.0.dev20261105152300",
            "1.0.2.1-SNAPSHOT, 1.0.2.1.dev20261105152300",
            "1.0.0-feature_name-SNAPSHOT, 1.0.0.dev20261105152300+feature.name",
            "1.0.0-feature/JIRA-42_Add-Login-SNAPSHOT, 1.0.0.dev20261105152300+feature.jira.42.add.login",
            "2.1.4-TR-123-SNAPSHOT, 2.1.4.dev20261105152300+tr.123",
            "1.0.1-3-SNAPSHOT, 1.0.1.dev20261105152300+3",
            "1.0.0-my-feature, 1.0.0+my.feature",
            // Produced by gitflow-maven-plugin 1.21.0 and jgitver 1.9.0 on feature branches.
            "1.0.0-login-SNAPSHOT, 1.0.0.dev20261105152300+login",
            "1.0.0-JIRA-42_Add-Login-SNAPSHOT, 1.0.0.dev20261105152300+jira.42.add.login",
            "1.0.0-Release-Candidate-Fixes-SNAPSHOT, 1.0.0.dev20261105152300+release.candidate.fixes",
            "1.0.0-user.profile_v2-SNAPSHOT, 1.0.0.dev20261105152300+user.profile.v2",
            "1.0.1-feature_jira_42_add_login-SNAPSHOT, 1.0.1.dev20261105152300+feature.jira.42.add.login",
            "1.0.1-bugfix_issue_7-SNAPSHOT, 1.0.1.dev20261105152300+bugfix.issue.7",
            // Branch names, not pre-releases.
            "1.0.0-a-team-SNAPSHOT, 1.0.0.dev20261105152300+a.team",
            "1.0.0-rc-fixes-SNAPSHOT, 1.0.0.dev20261105152300+rc.fixes",
            "1.0.0-beta_feature, 1.0.0+beta.feature",
            "1.0.0-b-SNAPSHOT, 1.0.0.dev20261105152300+b",
    })
    void convertsTheMavenVersionToPep440(String maven, String pep440) throws Exception {
        assertEquals(pep440, Versions.toPep440(maven, BUILD_START));
    }

    @Test
    void releasesDontDependOnTheBuildTime() throws Exception {
        assertEquals("2.0.0", Versions.toPep440("2.0.0", Instant.parse("2030-01-01T00:00:00Z")));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "1.0.0-M1 | Python has no milestone releases",
            "1.0.0-milestone-2-SNAPSHOT | Python has no milestone releases",
            "latest | it must start with a numeric release",
            "SNAPSHOT | it must start with a numeric release",
            "v1.0.0 | it must start with a numeric release",
    })
    void rejectsVersionsWithNoPep440Equivalent(String maven, String reason) {
        MojoExecutionException e = assertThrows(MojoExecutionException.class, () -> Versions.toPep440(maven, BUILD_START));

        assertTrue(e.getMessage().startsWith("Can't convert the Maven version " + maven + " "), e.getMessage());
        assertTrue(e.getMessage().contains(reason), e.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.0.0-SNAPSHOT", "1.0.0-snapshot", "1.0.0-feature_x-SNAPSHOT"})
    void snapshotsEndInSnapshotWhateverTheCase(String version) {
        assertTrue(Versions.isSnapshot(version));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.0.0", "SNAPSHOT-1.0.0", "1.0.0-SNAPSHOTS", "SNAPSHOT"})
    void otherVersionsArentSnapshots(String version) {
        assertFalse(Versions.isSnapshot(version));
    }

    @ParameterizedTest
    @CsvSource({
            "1.0.0, 1.0.0",
            "1.0.0.dev20261109092818, 1.0.0~~dev20261109092818",
            "1.0.0rc1, 1.0.0~rc1",
            "1.0a2, 1.0~a2",
            "1.0b1.dev3, 1.0~b1~dev3",
            "2.0.post1, 2.0^post1",
            "2.0.post1.dev2, 2.0^post1~dev2",
            "1.0.0.dev1+feature.login, 1.0.0~~dev1+feature.login",
            "1.0+Local.7, 1.0+local.7",
    })
    void convertsPep440ToRpm(String pep440, String rpm) {
        assertEquals(Optional.of(rpm), Versions.toRpm(pep440));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1!2.0", "1.0-SNAPSHOT", "1.0.0.dev", "latest"})
    void cantConvertWhatIsntANormalizedPep440VersionWithoutAnEpoch(String version) {
        assertEquals(Optional.empty(), Versions.toRpm(version));
    }
}
