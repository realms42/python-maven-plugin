package com.example.util;

public class PepVersion {
    public static String fromMaven(String mavenVersion) {
        return mavenVersion.replace("-SNAPSHOT", ".dev0");
    }
}
