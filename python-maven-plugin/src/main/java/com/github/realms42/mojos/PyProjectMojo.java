package com.github.realms42.mojos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.toml.TomlFactory;
import com.github.realms42.mojos.base.AbstractPythonMojo;

/**
 * Places a pyproject.toml in the work directory.
 *
 * <p>The project's own pyproject.toml is the source of truth: it is read from
 * the project root, given the Maven-derived version, and written to the work
 * directory. The file on disk is never modified. Projects without one get a
 * minimal generated file so a plain POM still builds.
 *
 * <p>The Maven version is only applied to a project that opts in with PEP 621
 * dynamic versioning ({@code dynamic = ["version"]}). A file that pins a
 * different {@code [project].version} fails the build rather than being
 * silently overridden.
 */
@Mojo(name = "generate-pyproject", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class PyProjectMojo extends AbstractPythonMojo {

    private static final String PYPROJECT = "pyproject.toml";
    private static final ObjectMapper TOML = new ObjectMapper(new TomlFactory());

    @Parameter(defaultValue = "${project.artifactId}", readonly = true)
    private String artifactId;

    @Parameter(defaultValue = "${project.version}", readonly = true)
    private String version;

    @Override
    public void execute() throws MojoExecutionException {
        Path source = basePath.toPath().resolve(PYPROJECT);
        boolean isNative = Files.exists(source);

        ObjectNode root = isNative ? read(source) : TOML.createObjectNode();
        if (isNative) {
            getLog().info("Using " + source);
        } else {
            getLog().info("No " + PYPROJECT + " in " + basePath + "; generating a minimal one.");
        }

        ObjectNode project = (ObjectNode) root.get("project");
        if (project == null || !project.isObject()) {
            project = TOML.createObjectNode();
            root.set("project", project);
        }
        applyName(project);
        applyVersion(project, isNative ? source.toString() : "The generated " + PYPROJECT);

        write(root);
    }

    private ObjectNode read(Path source) throws MojoExecutionException {
        try {
            JsonNode node = TOML.readTree(Files.readAllBytes(source));
            if (!node.isObject()) {
                throw new MojoExecutionException("Expected a table at the top level of " + source);
            }
            return (ObjectNode) node;
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to read " + source, e);
        }
    }

    /** Fill in the package name from the Maven artifactId, leaving a declared name alone. */
    private void applyName(ObjectNode project) {
        if (!project.hasNonNull("name")) {
            project.put("name", artifactId);
            return;
        }
        String declared = project.get("name").asText();
        if (!declared.equals(artifactId)) {
            getLog().info("[project].name is '" + declared + "'; keeping it (Maven artifactId is '"
                    + artifactId + "').");
        }
    }

    /**
     * Apply the Maven version, which requires the project to have opted in via
     * {@code dynamic = ["version"]}. A statically pinned version is honoured only
     * when it already matches; anything else is a conflict the user must resolve.
     */
    private void applyVersion(ObjectNode project, String source) throws MojoExecutionException {
        String derived = versionToPep440(version);
        boolean pinned = project.hasNonNull("version");
        boolean dynamic = declaresDynamicVersion(project);

        if (pinned && dynamic) {
            throw new MojoExecutionException(source + " sets [project].version and also lists \"version\" in"
                    + " [project].dynamic. PEP 621 allows one or the other, not both.");
        }

        if (dynamic) {
            project.put("version", derived);
            dropDynamicVersion(project);
            getLog().info("Set [project].version to " + derived + " from the Maven version.");
            return;
        }

        if (pinned) {
            String declared = project.get("version").asText();
            if (!declared.equals(derived)) {
                throw new MojoExecutionException(source + " pins [project].version to '" + declared
                        + "', but the Maven version is '" + version + "' (" + derived + " as PEP 440)."
                        + " Remove that line and add dynamic = [\"version\"] to let the Maven version apply,"
                        + " or change one of the two so they agree.");
            }
            getLog().info("[project].version is pinned to " + declared + ", which matches the Maven version.");
            return;
        }

        project.put("version", derived);
        getLog().info("Set [project].version to " + derived + " from the Maven version.");
    }

    private static boolean declaresDynamicVersion(ObjectNode project) {
        JsonNode dynamic = project.get("dynamic");
        if (dynamic == null || !dynamic.isArray()) {
            return false;
        }
        for (JsonNode entry : dynamic) {
            if ("version".equals(entry.asText())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Drop "version" from [project].dynamic. Once the version is written out as a
     * static value, leaving it marked dynamic makes the file invalid — build
     * backends reject a field that is both.
     */
    private static void dropDynamicVersion(ObjectNode project) {
        ArrayNode remaining = TOML.createArrayNode();
        for (JsonNode entry : project.get("dynamic")) {
            if (!"version".equals(entry.asText())) {
                remaining.add(entry);
            }
        }
        if (remaining.isEmpty()) {
            project.remove("dynamic");
        } else {
            project.set("dynamic", remaining);
        }
    }

    private void write(JsonNode node) throws MojoExecutionException {
        Path target = getWorkPath().resolve(PYPROJECT);
        try {
            Files.createDirectories(getWorkPath());
            Files.writeString(target, TOML.writerWithDefaultPrettyPrinter().writeValueAsString(node));
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to write " + target, e);
        }
        getLog().info("Written " + target);
    }

    private String versionToPep440(String mavenVersion) {
        // Strip common release qualifiers that mean "final release"
        String v = mavenVersion.replaceAll("(?i)[.\\-](RELEASE|Final|GA)$", "");

        // SNAPSHOT → .dev0
        v = v.replaceAll("(?i)-SNAPSHOT$", ".dev0");

        // Pre-release qualifiers: -alpha-N / -alphaN → aN
        v = v.replaceAll("(?i)-alpha-?(\\d+)$", "a$1");

        // -beta-N / -betaN → bN
        v = v.replaceAll("(?i)-beta-?(\\d+)$", "b$1");

        // -RC-N / -rcN / -RC-N → rcN
        v = v.replaceAll("(?i)-rc-?(\\d+)$", "rc$1");

        return v;
    }

}
