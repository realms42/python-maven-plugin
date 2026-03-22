package com.realms42.util;

public class Python {
    public static String versionFromMaven(String mavenVersion) {
        return mavenVersion.replace("-SNAPSHOT", ".dev0");
    }
}
