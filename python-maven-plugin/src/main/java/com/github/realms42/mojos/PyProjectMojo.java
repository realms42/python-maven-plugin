package com.github.realms42.mojos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.toml.TomlFactory;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.LifecyclePhase;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Mojo(name = "generate-pyproject", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class PyProjectMojo extends BaseMojo {

    @Parameter(defaultValue = "${project.artifactId}", readonly = true)
    private String artifactId;

    @Parameter(defaultValue = "${project.version}", readonly = true)
    private String version;

    @Parameter
    private Map<String, Object> pyProject;

    @Override
    public void execute() throws MojoExecutionException {
        ObjectMapper tomlMapper = new ObjectMapper(new TomlFactory());
        JsonNode userProject = pyProject != null
                ? tomlMapper.valueToTree(pyProject)
                : tomlMapper.createObjectNode();
        JsonNode finalProject = injectProjectMetadata(userProject);
        writePyProject(finalProject);
    }

    private void writePyProject(JsonNode node) throws MojoExecutionException {
        ObjectMapper tomlMapper = new ObjectMapper(new TomlFactory());
        ObjectMapper jsonMapper = new ObjectMapper();
        try {
            String tomlContent = tomlMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
            getLog().info("toml content: \n" + jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node));
            Path pyProjectPath = getWorkPath().resolve("pyproject.toml");
            Files.writeString(pyProjectPath, tomlContent);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to write pyproject.toml", e);
        }
        getLog().info("Written pyproject.toml");
    }

    private JsonNode injectProjectMetadata(JsonNode node) {
        ObjectNode copy = (ObjectNode) node.deepCopy();
        copy.putIfAbsent("project", new ObjectNode(new JsonNodeFactory(false)));
        ObjectNode project = (ObjectNode) copy.get("project");

        String defaultName = artifactId;
        String defaultVersion = versionToPep440(version);

        if (project.has("name")) {
            getLog().warn("[project].name is user-configured as '" + project.get("name").asText()
                    + "'; this overrides the recommended value '" + defaultName + "' derived from Maven artifactId."
                    + " Overriding is not recommended.");
        } else {
            project.put("name", defaultName);
        }

        if (project.has("version")) {
            getLog().warn("[project].version is user-configured as '" + project.get("version").asText()
                    + "'; this overrides the recommended value '" + defaultVersion + "' derived from Maven version."
                    + " Overriding is not recommended.");
        } else {
            project.put("version", defaultVersion);
        }

        return copy;
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
