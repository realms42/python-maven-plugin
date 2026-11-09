package io.github.realms42.mojos;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/**
 * Copies the project's wheel and sdist dependencies under their Python file names, and sets
 * {@code python.wheel.<artifactId>.*} and {@code python.sdist.<artifactId>.*} ({@code file}, {@code version},
 * {@code rpmVersion}) for the plugins that run after it, and {@code python.distribution.*} when there is only one.
 */
@Mojo(name = "distributions", defaultPhase = LifecyclePhase.GENERATE_RESOURCES,
        requiresDependencyResolution = ResolutionScope.RUNTIME, threadSafe = true)
public class DistributionsMojo extends BaseMojo {

    /** The directory the distributions are copied to. */
    @Parameter(property = "python.distributions.outputDirectory",
            defaultValue = "${project.build.directory}/distributions")
    private File outputDirectory;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Override
    public void execute() throws MojoExecutionException {
        List<Artifact> artifacts = project.getArtifacts().stream()
                .filter(a -> isWheel(a) || isSdist(a))
                .sorted(Comparator.comparing(Artifact::getId))
                .collect(Collectors.toList());
        if (artifacts.isEmpty()) {
            getLog().warn("The project has no wheel or sdist dependencies to copy.");
            return;
        }
        Path output = outputDirectory.toPath();
        Map<String, List<Distribution>> byKey = new LinkedHashMap<>();
        try {
            Files.createDirectories(output);
            for (Artifact artifact : artifacts) {
                Distribution distribution = isWheel(artifact) ? Wheel.read(artifact) : Sdist.read(artifact);
                Files.copy(artifact.getFile().toPath(), output.resolve(distribution.fileName()),
                        StandardCopyOption.REPLACE_EXISTING);
                getLog().info("Copied " + artifact.getId() + " as " + distribution.fileName());
                byKey.computeIfAbsent("python." + distribution.kind() + "." + artifact.getArtifactId() + ".",
                        k -> new ArrayList<>()).add(distribution);
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to copy the distributions to " + output, e);
        }
        for (Map.Entry<String, List<Distribution>> entry : byKey.entrySet()) {
            if (entry.getValue().size() > 1) {
                getLog().warn("Not setting " + entry.getKey() + "*: the project depends on "
                        + entry.getValue().size() + " of them.");
                continue;
            }
            setProperties(entry.getKey(), entry.getValue().get(0));
        }
        if (artifacts.size() == 1) {
            setProperties("python.distribution.", byKey.values().iterator().next().get(0));
        }
    }

    private static boolean isWheel(Artifact artifact) {
        return "whl".equals(artifact.getType());
    }

    private static boolean isSdist(Artifact artifact) {
        return artifact.getFile() != null && artifact.getFile().getName().endsWith(SDIST)
                && ("tar.gz".equals(artifact.getType()) || "python".equals(artifact.getType()));
    }

    private void setProperties(String prefix, Distribution distribution) {
        Properties properties = project.getProperties();
        properties.setProperty(prefix + "file", distribution.fileName());
        properties.setProperty(prefix + "version", distribution.version());
        Optional<String> rpmVersion = rpmVersion(distribution.version());
        if (rpmVersion.isPresent()) {
            properties.setProperty(prefix + "rpmVersion", rpmVersion.get());
        } else {
            getLog().warn("Not setting " + prefix + "rpmVersion: can't convert " + distribution.version()
                    + " to an RPM version.");
        }
    }

    // PEP 440 to RPM, as Fedora's Python packaging writes it: ~ for pre-releases, ~~ for a dev release of a final
    // release, so that it sorts before its pre-releases too, and ^ for post-releases.
    static Optional<String> rpmVersion(String pep440) {
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

    private static final Pattern PEP_440 = Pattern.compile(
            "(?:(\\d+)!)?(\\d+(?:\\.\\d+)*)(?:(a|b|rc)(\\d+))?(?:\\.post(\\d+))?(?:\\.dev(\\d+))?"
                    + "(?:\\+([a-z0-9]+(?:\\.[a-z0-9]+)*))?",
            Pattern.CASE_INSENSITIVE);

    interface Distribution {
        String kind();

        String version();

        String fileName();
    }

    static final class Wheel implements Distribution {
        final String name;
        final String version;
        final String build;
        final String tag;

        private Wheel(String name, String version, String build, String tag) {
            this.name = name;
            this.version = version;
            this.build = build;
            this.tag = tag;
        }

        @Override
        public String kind() {
            return "wheel";
        }

        @Override
        public String version() {
            return version;
        }

        @Override
        public String fileName() {
            return normalize(name) + "-" + version.replace('-', '_') + (build == null ? "" : "-" + build) + "-"
                    + tag + WHEEL;
        }

        static Wheel read(Artifact artifact) throws MojoExecutionException {
            File file = resolved(artifact, "wheel");
            try (ZipFile zip = new ZipFile(file)) {
                String distInfo = distInfo(zip, artifact);
                Map<String, List<String>> metadata = headers(zip, distInfo + "METADATA");
                Map<String, List<String>> wheel = headers(zip, distInfo + "WHEEL");
                String name = first(metadata, "Name");
                String version = first(metadata, "Version");
                List<String> tags = wheel.getOrDefault("Tag", List.of());
                if (name == null || version == null || tags.isEmpty()) {
                    throw new MojoExecutionException(artifact.getId() + " is not a valid wheel: its metadata has no"
                            + " Name, Version or Tag.");
                }
                return new Wheel(name, version, first(wheel, "Build"), compressedTag(tags));
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to read the wheel " + artifact.getId() + " (" + file + ")",
                        e);
            }
        }

        private static String distInfo(ZipFile zip, Artifact artifact) throws MojoExecutionException {
            Set<String> found = new LinkedHashSet<>();
            for (Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements(); ) {
                String entry = entries.nextElement().getName();
                if (entry.matches("[^/]+\\.dist-info/WHEEL")) {
                    found.add(entry.substring(0, entry.length() - "WHEEL".length()));
                }
            }
            if (found.size() != 1) {
                throw new MojoExecutionException(artifact.getId() + " is not a valid wheel: expected one .dist-info"
                        + " directory with a WHEEL file, found " + found.size() + ".");
            }
            return found.iterator().next();
        }

        private static Map<String, List<String>> headers(ZipFile zip, String path) throws IOException {
            ZipEntry entry = zip.getEntry(path);
            if (entry == null) {
                return new LinkedHashMap<>();
            }
            try (InputStream in = zip.getInputStream(entry)) {
                return DistributionsMojo.headers(in);
            }
        }

        // Tag: py2-none-any and Tag: py3-none-any are written py2.py3-none-any in the file name.
        private static String compressedTag(List<String> tags) throws MojoExecutionException {
            List<Set<String>> parts = List.of(new LinkedHashSet<>(), new LinkedHashSet<>(), new LinkedHashSet<>());
            for (String tag : tags) {
                String[] fields = tag.split("-");
                if (fields.length != 3) {
                    throw new MojoExecutionException("Invalid wheel tag: " + tag);
                }
                for (int i = 0; i < 3; i++) {
                    parts.get(i).add(fields[i]);
                }
            }
            return parts.stream().map(p -> String.join(".", p)).collect(Collectors.joining("-"));
        }
    }

    static final class Sdist implements Distribution {
        final String name;
        final String version;

        private Sdist(String name, String version) {
            this.name = name;
            this.version = version;
        }

        @Override
        public String kind() {
            return "sdist";
        }

        @Override
        public String version() {
            return version;
        }

        @Override
        public String fileName() {
            return normalize(name) + "-" + version + SDIST;
        }

        static Sdist read(Artifact artifact) throws MojoExecutionException {
            File file = resolved(artifact, "sdist");
            Map<String, List<String>> metadata = null;
            int found = 0;
            try (TarArchiveInputStream tar = new TarArchiveInputStream(
                    new GZIPInputStream(new BufferedInputStream(Files.newInputStream(file.toPath()))))) {
                for (TarArchiveEntry entry = tar.getNextEntry(); entry != null; entry = tar.getNextEntry()) {
                    if (entry.isFile() && entry.getName().matches("(\\./)?[^/]+/PKG-INFO")) {
                        found++;
                        metadata = headers(tar);
                    }
                }
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to read the sdist " + artifact.getId() + " (" + file + ")",
                        e);
            }
            if (found != 1) {
                throw new MojoExecutionException(artifact.getId() + " is not a valid sdist: expected one PKG-INFO in"
                        + " its top directory, found " + found + ".");
            }
            String name = first(metadata, "Name");
            String version = first(metadata, "Version");
            if (name == null || version == null) {
                throw new MojoExecutionException(artifact.getId() + " is not a valid sdist: its PKG-INFO has no"
                        + " Name or Version.");
            }
            return new Sdist(name, version);
        }
    }

    private static File resolved(Artifact artifact, String kind) throws MojoExecutionException {
        File file = artifact.getFile();
        if (file == null || !file.isFile()) {
            throw new MojoExecutionException("The " + kind + " " + artifact.getId() + " hasn't been resolved to a"
                    + " file.");
        }
        return file;
    }

    private static String normalize(String name) {
        return name.replaceAll("[-_.]+", "_").toLowerCase(Locale.ROOT);
    }

    // Leaves the stream open: a tar stream goes on to its next entry.
    private static Map<String, List<String>> headers(InputStream in) throws IOException {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        for (String line = reader.readLine(); line != null && !line.isEmpty(); line = reader.readLine()) {
            int colon = line.indexOf(':');
            if (colon > 0 && !Character.isWhitespace(line.charAt(0))) {
                headers.computeIfAbsent(line.substring(0, colon).trim(), k -> new ArrayList<>())
                        .add(line.substring(colon + 1).trim());
            }
        }
        return headers;
    }

    private static String first(Map<String, List<String>> headers, String name) {
        List<String> values = headers.get(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }
}
