package io.github.realms42.mojos;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.plugin.MojoExecutionException;

/** The version conversions: Maven to PEP 440, as described in the README's Versioning section, and PEP 440 to RPM. */
final class Versions {

    private Versions() {
    }

    static boolean isSnapshot(String maven) {
        return maven.regionMatches(true, Math.max(0, maven.length() - SNAPSHOT.length()), SNAPSHOT, 0,
                SNAPSHOT.length());
    }

    static String toPep440(String maven, Instant buildStart) throws MojoExecutionException {
        boolean snapshot = isSnapshot(maven);
        String v = snapshot ? maven.substring(0, maven.length() - SNAPSHOT.length()) : maven;
        Matcher parts = MAVEN_VERSION.matcher(v);
        if (!parts.matches()) {
            throw new MojoExecutionException("Can't convert the Maven version " + maven + " to a Python (PEP 440)"
                    + " version: it must start with a numeric release, such as 1.0.0.");
        }
        StringBuilder pep440 = new StringBuilder(parts.group(1));
        String qualifier = parts.group(2) == null ? "" : parts.group(2);
        String local = "";
        Matcher preRelease = PRE_RELEASE.matcher(qualifier);
        if (preRelease.matches()) {
            String label = preRelease.group(1).toLowerCase(Locale.ROOT);
            pep440.append(label.startsWith("a") ? "a" : label.startsWith("b") ? "b" : "rc");
            pep440.append(preRelease.group(2).isEmpty() ? "0" : preRelease.group(2));
        } else if (MILESTONE.matcher(qualifier).matches()) {
            throw new MojoExecutionException("Can't convert the Maven version " + maven + " to a Python (PEP 440)"
                    + " version: Python has no milestone releases. Use an alpha, beta or release candidate"
                    + " instead, such as -alpha-1 or -rc-1.");
        } else if (!RELEASE.matcher(qualifier).matches()) {
            local = qualifier.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", ".").replaceAll("^\\.|\\.$", "");
        }
        if (snapshot) {
            pep440.append(".dev").append(SNAPSHOT_TIMESTAMP.format(buildStart));
        }
        if (!local.isEmpty()) {
            pep440.append('+').append(local);
        }
        return pep440.toString();
    }

    // As Fedora's Python packaging writes it: ~ for pre-releases, ~~ for a dev release of a final release, so that
    // it sorts before its pre-releases too, and ^ for post-releases. RPM keeps an epoch in its own tag, so a version
    // with one has no RPM version.
    static Optional<String> toRpm(String pep440) {
        Matcher v = PEP_440.matcher(pep440);
        if (!v.matches() || v.group(1) != null) {
            return Optional.empty();
        }
        StringBuilder rpm = new StringBuilder(v.group(2));
        boolean suffixed = false;
        if (v.group(3) != null) {
            rpm.append('~').append(v.group(3).toLowerCase(Locale.ROOT)).append(v.group(4));
            suffixed = true;
        }
        if (v.group(5) != null) {
            rpm.append("^post").append(v.group(5));
            suffixed = true;
        }
        if (v.group(6) != null) {
            rpm.append(suffixed ? "~dev" : "~~dev").append(v.group(6));
        }
        if (v.group(7) != null) {
            rpm.append('+').append(v.group(7).toLowerCase(Locale.ROOT));
        }
        return Optional.of(rpm.toString());
    }

    private static final String SNAPSHOT = "-SNAPSHOT";
    private static final DateTimeFormatter SNAPSHOT_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);
    private static final Pattern MAVEN_VERSION = Pattern.compile("(\\d+(?:\\.\\d+)*)(?:[.-](.+))?");
    // As in Maven, `a` and `b` only count as alpha and beta when a number follows.
    private static final Pattern PRE_RELEASE =
            Pattern.compile("(alpha|beta|rc|cr|a(?=[.-]?\\d)|b(?=[.-]?\\d))[.-]?(\\d*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern MILESTONE =
            Pattern.compile("(milestone[.-]?\\d*|m[.-]?\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern RELEASE = Pattern.compile("release|final|ga", Pattern.CASE_INSENSITIVE);
    private static final Pattern PEP_440 = Pattern.compile(
            "(?:(\\d+)!)?(\\d+(?:\\.\\d+)*)(?:(a|b|rc)(\\d+))?(?:\\.post(\\d+))?(?:\\.dev(\\d+))?"
                    + "(?:\\+([a-z0-9]+(?:\\.[a-z0-9]+)*))?",
            Pattern.CASE_INSENSITIVE);
}
