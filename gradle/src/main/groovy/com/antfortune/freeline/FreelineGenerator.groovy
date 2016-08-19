package com.antfortune.freeline

import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.api.Project

import java.nio.charset.Charset
import java.security.MessageDigest

/**
 * Created by huangyong on 16/8/1.
 */
class FreelineGenerator {

    public static boolean isNormalProductFlavor(String productFlavor) {
        return productFlavor.equalsIgnoreCase("") || productFlavor.equalsIgnoreCase("debug")
    }

    public static String generateBuildScript(String productFlavor) {
        def params = []
        if (Os.isFamily(Os.FAMILY_WINDOWS)) {
            params.add("gradlew.bat")
        } else {
            params.add("./gradlew")
        }

        if (isNormalProductFlavor(productFlavor)) {
            params.add("assembleDebug")
        } else {
            params.add("assemble${productFlavor.capitalize()}Debug")
        }

        return params.join(" ")
    }

    public static String generateApkPath(String buildDir, String projectName, String productFlavor) {
        if (isNormalProductFlavor(productFlavor)) {
            return FreelineUtils.joinPath(buildDir, "outputs", "apk", "$projectName-debug.apk")
        } else {
            return FreelineUtils.joinPath(buildDir, "outputs", "apk", "$projectName-$productFlavor-debug.apk")
        }
    }

    public static String generateMainRPath(String buildDir, String productFlavor, String packageName) {
        String suffix = FreelineUtils.joinPath(packageName.replace(".", File.separator), "R.java")
        if (isNormalProductFlavor(productFlavor)) {
            return FreelineUtils.joinPath(buildDir, "generated", "source", "r", "debug", suffix)
        } else {
            return FreelineUtils.joinPath(buildDir, "generated", "source", "r", productFlavor, "debug", suffix)
        }
    }

    public static String generateProjectBuildAssetsPath(String buildDir, String productFlavor) {
        if (isNormalProductFlavor(productFlavor)) {
            return FreelineUtils.joinPath(buildDir, "intermediates", "assets", "debug")
        } else {
            return FreelineUtils.joinPath(buildDir, "intermediates", "assets", productFlavor, "debug")
        }
    }

    public static String generateProjectBuildJniFolderPath(String buildDir, String productFlavor) {
        if (isNormalProductFlavor(productFlavor)) {
            return FreelineUtils.joinPath(buildDir, "intermediates", "jniLibs", "debug")
        } else {
            return FreelineUtils.joinPath(buildDir, "intermediates", "jniLibs", productFlavor, "debug")
        }
    }

    public static String generateStringMD5(String input) {
        return MessageDigest.getInstance("MD5").digest(input.getBytes(Charset.forName("UTF-8"))).encodeHex().toString()
    }
    
}
