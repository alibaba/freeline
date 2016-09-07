package com.antfortune.freeline.plugin.utils;

/**
 * Created by micro on 2016/9/6 0006.
 */
public class SystemUtil {
    public static boolean isWindows() {
        String os = System.getenv("os");
        return os.equalsIgnoreCase("Windows_NT");
    }

    public static boolean hasPython() {
        return !System.getenv("python").isEmpty();
    }

    public static boolean hasAndroidSDK() {
        return !System.getenv("ANDROID_HOME").isEmpty();
    }
}
