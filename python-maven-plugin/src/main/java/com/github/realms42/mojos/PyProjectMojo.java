package com.github.realms42.mojos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.toml.TomlFactory;
import com.github.realms42.util.JsonMergeUtil;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.LifecyclePhase;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Mojo(name = "generate-pyproject", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class PyProjectMojo extends BaseMojo {

    @Parameter(defaultValue = "${project.artifactId}", readonly = true)
    private String artifactId;

    @Parameter(defaultValue = "${project.version}", readonly = true)
    private String version;

    @Parameter(defaultValue = "hatchling>=1.18")
    private String buildSystemRequires;

    @Parameter(defaultValue = "hatchling.build")
    private String buildBackend;

    @Parameter
    private List<String> testDependencies = Arrays.asList("pytest", "pytest-cov", "ruff");

    @Parameter
    private List<String> buildDependencies = Arrays.asList("build");

    @Parameter(defaultValue = "tests")
    private String pytestTestpaths;

    @Parameter(defaultValue = "tests")
    private String ruffExclude;

    @Parameter
    private Map<String, Object> pyProject;

    @Override
    public void execute() throws MojoExecutionException {
        ObjectMapper tomlMapper = new ObjectMapper(new TomlFactory());
        JsonNode finalProject = tomlMapper.createObjectNode();
        JsonNode defaultProject = buildDefaults();
        finalProject = JsonMergeUtil.merge(finalProject, defaultProject);
        JsonNode userProject = pyProject != null
                ? tomlMapper.valueToTree(pyProject)
                : tomlMapper.createObjectNode();
        finalProject = JsonMergeUtil.merge(finalProject, userProject);

        finalProject = injectProjectMetadata(finalProject);
        writePyProject(finalProject);
    }

    private JsonNode buildDefaults() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();

        // [project.optional-dependencies]
        ObjectNode project = mapper.createObjectNode();
        ObjectNode optionalDeps = mapper.createObjectNode();
        optionalDeps.set("test", listNode(mapper, testDependencies));
        optionalDeps.set("build", listNode(mapper, buildDependencies));
        project.set("optional-dependencies", optionalDeps);
        root.set("project", project);

        // [dependency-groups]
        ObjectNode depGroups = mapper.createObjectNode();
        depGroups.set("test", listNode(mapper, testDependencies));
        depGroups.set("build", listNode(mapper, buildDependencies));
        root.set("dependency-groups", depGroups);

        // [build-system]
        ObjectNode buildSystem = mapper.createObjectNode();
        buildSystem.set("requires", listNode(mapper, Arrays.asList(buildSystemRequires)));
        buildSystem.put("build-backend", buildBackend);
        root.set("build-system", buildSystem);

        // [tool.pytest.ini_options] and [tool.ruff]
        ObjectNode tool = mapper.createObjectNode();
        ObjectNode pytestIni = mapper.createObjectNode();
        pytestIni.set("testpaths", listNode(mapper, Arrays.asList(pytestTestpaths)));
        ObjectNode pytest = mapper.createObjectNode();
        pytest.set("ini_options", pytestIni);
        tool.set("pytest", pytest);
        ObjectNode ruff = mapper.createObjectNode();
        ruff.set("exclude", listNode(mapper, Arrays.asList(ruffExclude)));
        tool.set("ruff", ruff);
        root.set("tool", tool);

        return root;
    }

    private ArrayNode listNode(ObjectMapper mapper, List<String> items) {
        ArrayNode arr = mapper.createArrayNode();
        items.forEach(arr::add);
        return arr;
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

}
