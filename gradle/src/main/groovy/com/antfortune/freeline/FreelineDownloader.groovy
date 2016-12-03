package com.antfortune.freeline

import com.antfortune.freeline.versions.StaticVersionComparator
import com.antfortune.freeline.versions.VersionParser
import org.apache.commons.io.FileUtils
import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.api.GradleException
import org.gradle.api.Project

/**
 * Created by huangyong on 16/11/21.
 */
class FreelineDownloader {

    private static final String PARAM_MIRROR = "mirror"
    private static final String PARAM_VERSION = "freelineVersion"
    private static final String PARAM_TARGET_URL = "freelineTargetUrl"
    private static final String PARAM_CDN_URL = "freelineCdnUrl"
    private static final String PARAM_LOCAL = "freelineLocal"

    private static final String GITHUB_API = "https://api.github.com/repos/alibaba/freeline/releases/latest"
    private static final String FREELINE_API = "https://www.freelinebuild.com/api/versions/latest"
    private static final String CDN_URL = "http://static.freelinebuild.com/freeline"

    public static void execute(Project project) {
        def mirror = project.hasProperty(PARAM_MIRROR)
        String freelineVersion = FreelineUtils.getProperty(project, PARAM_VERSION)
        String cdnUrl = FreelineUtils.getProperty(project, PARAM_CDN_URL)
        String localPath = FreelineUtils.getProperty(project, PARAM_LOCAL)
        String targetUrl = FreelineUtils.getProperty(project, PARAM_TARGET_URL)

        if (FreelineUtils.isEmpty(cdnUrl)) {
            cdnUrl = CDN_URL
        }

        if (!FreelineUtils.isEmpty(localPath)) {
            targetUrl = new File(project.rootDir, localPath).absolutePath
        }

        if (FreelineUtils.isEmpty(targetUrl)) {
            if (freelineVersion) {
                println "[NOTE] Download freeline dependency for specific version ${freelineVersion}..."
                targetUrl = getDownloadUrl(cdnUrl, freelineVersion, true)
            } else {
                targetUrl = fetchData(project, cdnUrl, mirror)
                if (FreelineUtils.isEmpty(targetUrl)) {
                    throw new GradleException("Download Error: failed to get download url from: \n" +
                            "    1. ${FREELINE_API}\n" +
                            "    2. ${GITHUB_API}\n")
                }
            }
        }

        def ant = new AntBuilder()
        if (FreelineUtils.isEmpty(localPath)) {
            println "Downloading release pack from ${targetUrl}"
            println "Please wait a minute..."
            def downloadFile = new File(project.rootDir, "freeline.zip.tmp")
            if (downloadFile.exists()) {
                downloadFile.delete()
            }

            ant.get(src: targetUrl, dest: downloadFile)
            downloadFile.renameTo("freeline.zip")
            println 'download success.'
        } else {
            File localFile = getRealFile(project.rootDir.absolutePath, localPath)
            if (localFile == null) {
                throw new GradleException("File not found for freelineLocal: -PfreelineLocal=${localPath}")
            }

            File targetFile = new File(project.rootDir, "freeline.zip")
            FileUtils.copyFile(localFile, targetFile)
            println "Download freeline.zip from disk path: ${localFile.absolutePath}"
        }

        def freelineDir = new File(project.rootDir, "freeline")
        if (freelineDir.exists()) {
            FileUtils.deleteDirectory(freelineDir)
            println 'removing existing freeline directory'
        }
        ant.unzip(src: "freeline.zip", dest: project.rootDir.absolutePath)
        println 'unziped freeline.zip.'

        if (FreelineUtils.isWindows()) {
            FileUtils.deleteQuietly(new File(project.rootDir, "freeline_core"))
            FileUtils.deleteQuietly(new File(project.rootDir, "freeline.py"))
            FileUtils.copyDirectory(new File(freelineDir, "freeline_core"), new File(project.rootDir, "freeline_core"));
            FileUtils.copyFile(new File(freelineDir, "freeline.py"), new File(project.rootDir, "freeline.py"))
        } else {
            Runtime.getRuntime().exec("chmod -R +x freeline")
            Runtime.getRuntime().exec("ln -s freeline/freeline.py freeline.py")
        }

        def freelineZipFile = new File(project.rootDir, "freeline.zip")
        if (freelineZipFile.exists()) {
            freelineZipFile.delete()
        }
    }

    private static File getRealFile(String rootDirPath, String freelineLocal) {
        if (!FreelineUtils.isEmpty(freelineLocal)) {
            File localFile;
            if (freelineLocal.contains(File.separator)) {
                localFile = new File(freelineLocal)
            } else {
                localFile = new File(rootDirPath, freelineLocal)
            }
            return localFile.exists() ? localFile : null
        }
        return null
    }

    private static String getDownloadUrl(String cdnUrl, String version, boolean ignoreOs) {
        if (ignoreOs) {
            return "${cdnUrl}/${version}/all/freeline.zip"
        }

        if (Os.isFamily(Os.FAMILY_WINDOWS)) {
            return "${cdnUrl}/${version}/win/freeline.zip"
        } else if (Os.isFamily(Os.FAMILY_MAC)) {
            return "${cdnUrl}/${version}/mac/freeline.zip"
        } else if (Os.isFamily(Os.FAMILY_UNIX)) {
            return "${cdnUrl}/${version}/linux/freeline.zip"
        } else {
            throw new GradleException("Unknown system os!!!")
        }
    }

    private static String fetchData(Project project, String cdnUrl, boolean mirror) {
        def json = fetchDataFromAPI(project)
        if (json != null) {
            String version = json.freelineVersion.version
            checkVersion(project, version)
            return getDownloadUrl(mirror, cdnUrl, version, json.freelineVersion.download_url as String)
        }

        json = fetchDataFromGithub(project)
        if (json != null) {
            String version = json.name
            checkVersion(project, version)
            return getDownloadUrl(mirror, cdnUrl, version, json.assets[0].browser_download_url as String)
        }
        return null
    }

    private static String getDownloadUrl(boolean mirror, String cdnUrl, String version, String directUrl) {
        if (mirror) {
            println "[NOTE] Download freeline dependency from mirror..."
            return getDownloadUrl(cdnUrl, version, false)
        } else {
            return directUrl
        }
    }

    private static def fetchDataFromAPI(Project project) {
        try {
            def json = FreelineUtils.getJson(FREELINE_API)
            return json
        } catch (Exception e) {
            println "[ERROR] Fetching data from api occurs error"
            return null
        }
    }

    private static def fetchDataFromGithub(Project project) {
        try {
            def json = FreelineUtils.getJson(GITHUB_API)
            if (json == null || json == '') {
                println "Download Error: failed to get json from ${GITHUB_API}"
                return null
            }
            return json
        } catch (Exception e) {
            println "[ERROR] Fetching data from github occurs error: ${e.getMessage()}"
            return null
        }
    }

    private static void checkVersion(Project project, String latestVersion) {
        String freelineGradleVersion = FreelineUtils.getFreelineGradlePluginVersion(project)
        int result = isFreelineGradleVersionNeedToBeUpdated(freelineGradleVersion, latestVersion)
        if (result < 0) {
            throw new GradleException("Your local freeline version ${freelineGradleVersion} is lower than " +
                    "the lastest release version ${latestVersion}. Please update the freeline version in " +
                    "build.gradle. If you still want the specific version of freeline, you can execute the " +
                    "initial command with the extra parameter `-PfreelineVersion={your-wanted-version}`. " +
                    "eg: `gradlew initFreeline -PfreelineVersion=${freelineGradleVersion}`")
        } else if (result > 0) {
            println "[WARNING] Your local freeline version ${freelineGradleVersion} is greater than the " +
                    "lastest release version ${latestVersion}."
        }
    }

    private static int isFreelineGradleVersionNeedToBeUpdated(String freelineGradleVersion, String lastestVersion) {
        if (FreelineUtils.isEmpty(freelineGradleVersion) || FreelineUtils.isEmpty(lastestVersion)) {
            return 0
        }
        // Use custom class to avoid java.lang.NoClassDefFoundError in lower gradle version.
        VersionParser versionParser = new VersionParser()
        int result = new StaticVersionComparator().compare(versionParser.transform(freelineGradleVersion),
                versionParser.transform(lastestVersion))
        return result
    }

}
