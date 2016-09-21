package com.antfortune.freeline.plugin.utils;

import com.intellij.openapi.util.SystemInfo;

/**
 * System Utility Wrapper
 *
 * @author act262@gmail.com
 */
public class SystemUtil {

    /**
     * os system is windows or other
     */
    public static boolean isWindows() {
//        String os = System.getenv("os");
//        return os.equalsIgnoreCase("Windows_NT");

        return SystemInfo.isWindows;
    }

    public static boolean hasPython() {
        return !System.getenv("python").isEmpty();
    }

    public static boolean hasAndroidSDK() {
        return !System.getenv("ANDROID_HOME").isEmpty();
    }

}
