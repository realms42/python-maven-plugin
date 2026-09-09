package com.github.realms42.mojos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.toml.TomlFactory;
import com.github.realms42.mojos.base.AbstractPythonMojo;

@Mojo(name = "generate-pyproject", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class PyProjectMojo extends AbstractPythonMojo {

    private static final ObjectMapper TOML = new ObjectMapper(new TomlFactory());
    private static final ObjectMapper JSON = new ObjectMapper();

    @Parameter(defaultValue = "${project.artifactId}", readonly = true)
    private String artifactId;

    @Parameter(defaultValue = "${project.version}", readonly = true)
    private String version;

    @Parameter
    private Map<String, Object> pyProject;

    @Override
    public void execute() throws MojoExecutionException {
        JsonNode userProject = pyProject != null ? TOML.valueToTree(pyProject) : TOML.createObjectNode();
        writePyProject(injectProjectMetadata(userProject));
    }

    private void writePyProject(JsonNode node) throws MojoExecutionException {
        try {
            String tomlContent = TOML.writerWithDefaultPrettyPrinter().writeValueAsString(node);
            getLog().info("toml content: \n" + JSON.writerWithDefaultPrettyPrinter().writeValueAsString(node));
            Path pyProjectPath = getWorkPath().resolve("pyproject.toml");
            Files.writeString(pyProjectPath, tomlContent);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to write pyproject.toml", e);
        }
        getLog().info("Written pyproject.toml");
    }

    private JsonNode injectProjectMetadata(JsonNode node) {
        ObjectNode copy = (ObjectNode) node.deepCopy();
        copy.putIfAbsent("project", TOML.createObjectNode());
        ObjectNode project = (ObjectNode) copy.get("project");

        applyDerivedDefault(project, "name", artifactId, "artifactId");
        applyDerivedDefault(project, "version", versionToPep440(version), "version");

        return copy;
    }

    /**
     * Fill in a {@code [project]} field derived from the Maven POM, leaving any
     * user-configured value in place with a warning.
     */
    private void applyDerivedDefault(ObjectNode project, String field, String derived, String mavenSource) {
        if (project.has(field)) {
            getLog().warn("[project]." + field + " is user-configured as '" + project.get(field).asText()
                    + "'; this overrides the recommended value '" + derived + "' derived from Maven " + mavenSource + "."
                    + " Overriding is not recommended.");
        } else {
            project.put(field, derived);
        }
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
