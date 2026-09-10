package com.github.realms42.mojos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

@Mojo(name = "generate-pyproject", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class PyProjectMojo extends AbstractPythonMojo {

    private static final String PYPROJECT = "pyproject.toml";
    private static final String BACKEND = "hatchling";
    private static final ObjectMapper TOML = new ObjectMapper(new TomlFactory());

    @Parameter(defaultValue = "${project.artifactId}", readonly = true)
    private String artifactId;

    @Parameter(defaultValue = "${project.version}", readonly = true)
    private String version;

    @Override
    public void execute() throws MojoExecutionException {
        Path source = basePath.toPath().resolve(PYPROJECT);
        boolean isNative = Files.exists(source);

        ObjectNode root;
        if (isNative) {
            getLog().info("Using " + source);
            root = read(source);
        } else {
            getLog().info("No " + PYPROJECT + " in " + basePath + "; generating a minimal one.");
            root = generate();
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

    private ObjectNode generate() {
        ObjectNode root = TOML.createObjectNode();
        ObjectNode buildSystem = root.putObject("build-system");
        buildSystem.set("requires", TOML.createArrayNode().add(BACKEND));
        buildSystem.put("build-backend", BACKEND + ".build");

        findSolePackage().ifPresent(name -> root.putObject("tool")
                .putObject("hatch")
                .putObject("build")
                .putObject("targets")
                .putObject("wheel")
                .set("packages", TOML.createArrayNode().add("src/" + name)));
        return root;
    }

    private Optional<String> findSolePackage() {
        Path src = getWorkPath().resolve("src");
        if (!Files.isDirectory(src)) {
            return Optional.empty();
        }
        try (Stream<Path> entries = Files.list(src)) {
            List<String> names = entries.filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .filter(n -> !n.startsWith(".") && !n.endsWith(".egg-info") && !n.equals("__pycache__"))
                    .collect(Collectors.toList());
            return names.size() == 1 ? Optional.of(names.get(0)) : Optional.empty();
        } catch (IOException e) {
            getLog().debug("Could not inspect " + src + ": " + e.getMessage());
            return Optional.empty();
        }
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
        String v = mavenVersion.replaceAll("(?i)[.\\-](RELEASE|Final|GA)$", "");
        v = v.replaceAll("(?i)-SNAPSHOT$", ".dev0");
        v = v.replaceAll("(?i)-alpha-?(\\d+)$", "a$1");
        v = v.replaceAll("(?i)-beta-?(\\d+)$", "b$1");
        v = v.replaceAll("(?i)-rc-?(\\d+)$", "rc$1");
        return v;
    }

}
