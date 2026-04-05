package com.github.realms42.mojos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.toml.TomlFactory;
import com.github.realms42.util.JsonMergeUtil;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.LifecyclePhase;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@Mojo(name = "generate-pyproject", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class PyProjectMojo extends BaseMojo {

    @Parameter(defaultValue = "${project.artifactId}", readonly = true)
    private String artifactId;

    @Parameter(defaultValue = "${project.version}", readonly = true)
    private String version;

    @Parameter(readonly = true)
    private Map<String, Object> pyProject;

    @Override
    public void execute() throws MojoExecutionException {
        ObjectMapper tomlMapper = new ObjectMapper(new TomlFactory());
        JsonNode finalProject = tomlMapper.createObjectNode();
        JsonNode defaultProject = loadDefaults();
        finalProject = JsonMergeUtil.merge(finalProject, defaultProject);
        JsonNode userProject = pyProject != null
                ? tomlMapper.valueToTree(pyProject)
                : tomlMapper.createObjectNode();
        finalProject = JsonMergeUtil.merge(finalProject, userProject);

        finalProject = injectProjectMetadata(finalProject);
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
        if (project.has("name")) {
            getLog().warn("Overriding [project] name=" + project.get("name") + " with " + artifactId);
        }
        if (project.has("version")) {
            getLog().warn("Overriding [project] version=" + project.get("version") + " with " + version);
        }

        project.put("name", artifactId);
        String pythonVersion = versionToPythonVersion(version);
        project.put("version", pythonVersion);
        return copy;
    }

    private String versionToPythonVersion(String mavenVersion) {
        if (mavenVersion.endsWith("-SNAPSHOT")) {
            return mavenVersion.replace("-SNAPSHOT", ".dev0");
        }
        return mavenVersion;
    }

    private JsonNode loadDefaults() throws MojoExecutionException {
        try (InputStream is = getClass().getResourceAsStream("/pyproject.toml")) {
            ObjectMapper tomlMapper = new ObjectMapper(new TomlFactory());
            JsonNode defaults = tomlMapper.readTree(is);
            return defaults;
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to parse bundled pyproject.toml", e);
        }
    }

}