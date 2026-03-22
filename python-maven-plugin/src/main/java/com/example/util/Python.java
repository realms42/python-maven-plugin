package com.example.util;

public class Python {
    public static String versionFromMaven(String mavenVersion) {
        return mavenVersion.replace("-SNAPSHOT", ".dev0");
    }
}
