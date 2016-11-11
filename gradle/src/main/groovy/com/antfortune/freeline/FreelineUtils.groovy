package com.antfortune.freeline

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import org.gradle.api.Project

import java.nio.channels.FileChannel

/**
 * Created by huangyong on 16/7/19.
 */
class FreelineUtils {

    public static String getRelativePath(File root, File target) {
        String path = target.absolutePath.replace(root.absolutePath, "")
        while (path.startsWith("/") || (path.startsWith("\\"))) {
            path = path.substring(1)
        }
        return path
    }

    public static def getProperty(Project project, String property) {
        if (project.hasProperty(property)) {
            return project.getProperties()[property];
        }
        return null;
    }

    public static String getFreelineCacheDir(String rootDirPath) {
        String projectCacheDir = FreelineGenerator.generateStringMD5(rootDirPath)
        def freelineCacheDir = new File(FreelineUtils.joinPath(System.properties['user.home'].toString(), ".freeline", "cache", projectCacheDir))
        if (!freelineCacheDir.exists() || !freelineCacheDir.isDirectory()) {
            freelineCacheDir.mkdirs()
        }
        return freelineCacheDir.absolutePath
    }

    public static String getDefaultApkPath(List<String> apks, String buildDir, String projectName, String productFlavor) {
        if (apks == null || apks.size() == 0) {
            return FreelineGenerator.generateApkPath(buildDir, projectName, productFlavor)
        }
        if (apks.size() == 1) {
            return apks.get(0)
        }
        for (String path : apks) {
            if (path.contains("-armeabi-")) {
                return path
            }
        }
        return apks.get(0)
    }

    public static String getBuildCacheDir(String buildDirPath) {
        def buildCacheDir = new File(buildDirPath, Constants.FREELINE_BUILD_CACHE_DIR)
        if (!buildCacheDir.exists() || !buildCacheDir.isDirectory()) {
            buildCacheDir.mkdirs()
        }
        return buildCacheDir.absolutePath
    }

    public static String getBuildAssetsDir(String buildDirPath) {
        def buildAssetsDir = new File(getBuildCacheDir(buildDirPath), "freeline-assets")
        if (!buildAssetsDir.exists() || !buildAssetsDir.isDirectory()) {
            buildAssetsDir.mkdirs()
        }
        return buildAssetsDir.absolutePath
    }

    public static String getBuildBackupDir(String buildDirPath) {
        def buildBackupDir = new File(getBuildCacheDir(buildDirPath), "freeline-backup")
        if (!buildBackupDir.exists() || !buildBackupDir.isDirectory()) {
            buildBackupDir.mkdirs()
        }
        return buildBackupDir.absolutePath
    }

    public static def getJson(String url) {
        return new JsonSlurper().parseText(new URL(url).text)
    }

    public static boolean saveJson(String json, String fileName, boolean override) {
        def pending = new File(fileName)
        if (pending.exists() && pending.isFile()) {
            if (override) {
                println "Old file $pending.absolutePath removed."
                pending.delete()
            } else {
                println "File $pending.absolutePath exists."
                return false
            }
        }

        pending << json
        println "Save to $pending.absolutePath"
        return true
    }

    public static void addNewAttribute(Project project, String key, def value) {
        def description = readProjectDescription(project)
        if (description != null) {
            description[key] = value
            saveJson(new JsonBuilder(description).toPrettyString(), joinPath(getFreelineCacheDir(project.rootDir.absolutePath), 'project_description.json'), true)
        }
    }

    public static def readProjectDescription(Project project) {
        def descriptionFile = new File(joinPath(getFreelineCacheDir(project.rootDir.absolutePath), 'project_description.json'))
        if (descriptionFile.exists()) {
            def description = new JsonSlurper().parseText(descriptionFile.text)
            return description
        }
        return null
    }

    public static String joinPath(String... sep) {
        if (sep.length == 0) {
            return "";
        }
        if (sep.length == 1) {
            return sep[0];
        }

        return new File(sep[0], joinPath(Arrays.copyOfRange(sep, 1, sep.length))).getPath();
    }

    public static String getOsName() {
        return System.getProperty("os.name");
    }

    public static boolean isWindows() {
        return getOsName().startsWith("Windows");
    }

    public static boolean isEmpty(String text) {
        return text == null || text == '' || text.trim() == ''
    }

}
