package com.github.realms42.mojos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

class PluginWiringTest {

    private static final String PLUGIN = "com.github.realms42:python-maven-plugin:";

    @Test
    void pythonPackagingBindsTheGoalsToTheLifecycle() throws Exception {
        Element phases = (Element) load("/META-INF/plexus/components.xml").getElementsByTagName("phases").item(0);
        Map<String, List<String>> bindings = new LinkedHashMap<>();
        for (Element phase : children(phases)) {
            bindings.put(phase.getTagName(), Arrays.stream(phase.getTextContent().split(","))
                    .map(String::trim)
                    .collect(Collectors.toList()));
        }

        assertEquals(Map.of(
                "compile", List.of(PLUGIN + "build"),
                "test", List.of(PLUGIN + "test"),
                "package", List.of(PLUGIN + "package"),
                "verify", List.of(PLUGIN + "lint", PLUGIN + "format"),
                "install", List.of("org.apache.maven.plugins:maven-install-plugin:3.1.2:install"),
                "deploy", List.of("org.apache.maven.plugins:maven-deploy-plugin:3.1.2:deploy")),
                bindings);
    }

    @ParameterizedTest
    @ValueSource(strings = {"build", "test", "package", "lint", "format", "publish"})
    void goalIsThreadSafe(String goal) throws Exception {
        assertEquals("true", child(mojo(goal), "threadSafe").getTextContent());
    }

    @ParameterizedTest
    @ValueSource(strings = {"build", "test", "package", "lint", "format", "publish"})
    void goalAndEveryParameterAUserCanSetAreDocumented(String goal) throws Exception {
        Element mojo = mojo(goal);
        assertFalse(child(mojo, "description").getTextContent().isBlank(), goal + " has no description");
        for (Element parameter : children(child(mojo, "parameters"))) {
            if (Boolean.parseBoolean(child(parameter, "editable").getTextContent())) {
                assertFalse(child(parameter, "description").getTextContent().isBlank(),
                        goal + "'s parameter " + child(parameter, "name").getTextContent() + " has no description");
            }
        }
    }

    @ParameterizedTest
    @CsvSource({
            "build, compile",
            "test, test",
            "package, package",
            "lint, verify",
            "format, verify",
            "publish, deploy",
    })
    void goalDefaultsToItsPhase(String goal, String phase) throws Exception {
        assertEquals(phase, child(mojo(goal), "phase").getTextContent());
    }

    @ParameterizedTest
    @CsvSource({
            "lint, true", "format, true",
            "build, false", "test, false", "package, false", "publish, false",
    })
    void onlyTheRuffGoalsTakeTheRuffParameters(String goal, boolean ruff) throws Exception {
        for (String name : List.of("ruffVersion", "ruffSettings")) {
            boolean has = children(child(mojo(goal), "parameters")).stream()
                    .anyMatch(p -> name.equals(child(p, "name").getTextContent()));
            assertEquals(ruff, has, goal + " / " + name);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "lint, ruffVersion, " + BaseMojo.RUFF_VERSION,
            "format, ruffVersion, " + BaseMojo.RUFF_VERSION,
            "test, pytestVersion, " + BaseMojo.PYTEST_VERSION,
    })
    void toolVersionsArePinnedByDefault(String goal, String parameter, String version) throws Exception {
        assertEquals(version, child(child(mojo(goal), "configuration"), parameter).getAttribute("default-value"));
    }

    @Test
    void lintEnforcesTheBaseRulesByDefault() throws Exception {
        Element enforcedRules = child(child(mojo("lint"), "configuration"), "enforcedRules");
        assertEquals("E,W,F,I,B,UP", enforcedRules.getAttribute("default-value"));
    }

    @ParameterizedTest
    @CsvSource({
            "test, skipTests, skipTests",
            "test, skip, maven.test.skip",
            "lint, skip, python.lint.skip",
            "lint, enforcedRules, python.lint.enforcedRules",
            "lint, ruffVersion, python.ruff.version",
            "format, ruffVersion, python.ruff.version",
            "test, pytestVersion, python.pytest.version",
            "test, testWheel, python.test.wheel",
            "format, skip, python.format.skip",
            "publish, skipPublish, python.publish.skip",
            "publish, skipReleases, python.publish.skipReleases",
            "publish, skipSnapshots, python.publish.skipSnapshots",
    })
    void parameterIsSetByItsUserProperty(String goal, String parameter, String property) throws Exception {
        assertEquals("${" + property + "}", child(child(mojo(goal), "configuration"), parameter).getTextContent());
    }

    @ParameterizedTest
    @CsvSource({
            "releaseRepositoryId, python.publish.releaseRepository.id",
            "releaseRepositoryUrl, python.publish.releaseRepository.url",
            "snapshotRepositoryId, python.publish.snapshotRepository.id",
            "snapshotRepositoryUrl, python.publish.snapshotRepository.url",
    })
    void repositoryValuesAreReadFromProperties(String parameter, String property) throws Exception {
        Element configured = child(child(mojo("publish"), "configuration"), parameter);

        assertEquals("${" + property + "}", configured.getAttribute("default-value"));
        assertEquals("", configured.getTextContent());
    }

    @ParameterizedTest
    @CsvSource({
            "releaseRepository, true",
            "snapshotRepository, true",
            "releaseRepositoryId, false",
            "releaseRepositoryUrl, false",
            "snapshotRepositoryId, false",
            "snapshotRepositoryUrl, false",
    })
    void repositoriesAreBlocksInThePomAndPlainValuesAsProperties(String name, String editable) throws Exception {
        Element parameter = parameter(mojo("publish"), name);

        assertEquals(editable, child(parameter, "editable").getTextContent());
        if (Boolean.parseBoolean(editable)) {
            assertEquals(PublishRepository.class.getName(), child(parameter, "type").getTextContent());
        }
    }

    private static Element parameter(Element mojo, String name) {
        for (Element parameter : children(child(mojo, "parameters"))) {
            if (name.equals(child(parameter, "name").getTextContent())) {
                return parameter;
            }
        }
        throw new AssertionError("The '" + child(mojo, "goal").getTextContent() + "' goal has no parameter " + name);
    }

    private static Element mojo(String goal) throws Exception {
        Element mojos = child(load("/META-INF/maven/plugin.xml"), "mojos");
        for (Element mojo : children(mojos)) {
            if (goal.equals(child(mojo, "goal").getTextContent())) {
                return mojo;
            }
        }
        throw new AssertionError("The plugin descriptor has no '" + goal + "' goal");
    }

    private static Element load(String resource) throws Exception {
        try (InputStream in = PluginWiringTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, resource + " is not on the classpath");
            return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in).getDocumentElement();
        }
    }

    private static Element child(Element parent, String name) {
        for (Element child : children(parent)) {
            if (child.getTagName().equals(name)) {
                return child;
            }
        }
        throw new AssertionError("<" + parent.getTagName() + "> has no <" + name + ">");
    }

    private static List<Element> children(Element parent) {
        List<Element> children = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element) {
                children.add((Element) node);
            }
        }
        return children;
    }
}
