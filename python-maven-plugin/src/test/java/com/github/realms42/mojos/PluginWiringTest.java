package com.github.realms42.mojos;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.w3c.dom.Element;
import org.w3c.dom.Node;

// The unit tests set parameters directly, so this checks the Maven-facing side: the generated
// plugin descriptor (phases, user properties) and the lifecycle for <packaging>python</packaging>.
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
            "test, skipTests, skipTests",
            "test, skip, maven.test.skip",
            "lint, skip, python.lint.skip",
            "format, skip, python.format.skip",
            "publish, skipPublish, python.publish.skip",
            "publish, repositoryUrl, python.publish.url",
            "publish, snapshotRepositoryUrl, python.publish.snapshotUrl",
            "publish, serverId, python.publish.serverId",
    })
    void parameterIsSetByItsUserProperty(String goal, String parameter, String property) throws Exception {
        assertEquals("${" + property + "}", child(child(mojo(goal), "configuration"), parameter).getTextContent());
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
