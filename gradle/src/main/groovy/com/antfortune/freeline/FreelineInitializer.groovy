package com.antfortune.freeline

import groovy.json.JsonBuilder
import org.apache.commons.io.FileUtils
import org.gradle.api.Project
import org.gradle.api.artifacts.UnknownConfigurationException

/**
 * Created by huangyong on 16/7/19.
 */
class FreelineInitializer {

    private static final String LATEST_RELEASE_URL = "https://api.github.com/repos/alibaba/freeline/releases/latest";

    public static void initFreeline(Project project) {
        println "Freeline init process start..."

        def mirror = project.hasProperty("mirror")
        def snapshot = project.hasProperty("snapshot")

        if (mirror) {
            println "[NOTE] Download freeline dependency from mirror..."
        }

        if (snapshot) {
            println "[NOTE] Download freeline snapshot enabled..."
        }

        def json = FreelineUtils.getJson(LATEST_RELEASE_URL)
        if (json == null || json == '') {
            println "Download Error: failed to get json from ${LATEST_RELEASE_URL}"
            return
        }

        def url
        if (snapshot) {
            url = "http://obr0ndq7a.bkt.clouddn.com/freeline/snapshot.zip"
        } else {
            if (mirror) {
                url = "http://obr0ndq7a.bkt.clouddn.com/freeline/${json.assets[0].name}"
            } else {
                url = json.assets[0].browser_download_url
            }
        }
        println "Downloading lastest release from ${url}"
        println "Please wait a minute..."
        def downloadFile = new File(project.rootDir, "freeline.zip.tmp")
        if (downloadFile.exists()) {
            downloadFile.delete()
        }

        def ant = new AntBuilder()
        ant.get(src: url, dest: downloadFile)
        downloadFile.renameTo("freeline.zip")
        println 'download success.'


        def freelineDir = new File(project.rootDir, "freeline")
        if (freelineDir.exists()) {
            FileUtils.deleteDirectory(freelineDir)
            println 'removing existing freeline directory'
        }
        ant.unzip(src: "freeline.zip", dest: project.rootDir.absolutePath)
        println 'unziped freeline.zip.'

        if (FreelineUtils.isWindows()) {
            FileUtils.deleteDirectory(new File(project.rootDir, "freeline_core"))
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

        generateProjectDescription(project)
    }

    public static void generateProjectDescription(Project project) {
        def extension = project.extensions.findByName("freeline") as FreelineExtension
        def productFlavor = extension.productFlavor
        def buildScript = extension.buildScript
        def buildScriptWorkDirectory = extension.buildScriptWorkDirectory
        def apkPath = extension.apkPath
        def extraResourcesDependencies = extension.extraResourceDependencyPaths
        def excludeResourceDependencyPaths = extension.excludeResourceDependencyPaths

        def projectDescription = [:]

        projectDescription.project_type = 'gradle'
        projectDescription.java_home = getJavaHome()
        projectDescription.freeline_cache_dir = FreelineUtils.getFreelineCacheDir(project.rootDir.absolutePath)
        projectDescription.product_flavor = productFlavor
        projectDescription.build_script = buildScript
        projectDescription.build_script_work_directory = buildScriptWorkDirectory
        projectDescription.root_dir = project.rootDir.toString()
        projectDescription.main_project_name = project.name
        projectDescription.main_project_dir = FreelineUtils.getRelativePath(project.rootProject.projectDir, project.projectDir)
        projectDescription.build_directory = project.buildDir.toString()
        projectDescription.build_cache_dir = FreelineUtils.getBuildCacheDir(project.buildDir.absolutePath)
        projectDescription.build_tools_version = project.android.buildToolsVersion.toString()
        projectDescription.sdk_directory = project.android.sdkDirectory.toString()
        projectDescription.build_tools_directory = FreelineUtils.joinPath(projectDescription.sdk_directory, 'build-tools', projectDescription.build_tools_version)
        projectDescription.compile_sdk_version = project.android.compileSdkVersion.toString()
        projectDescription.compile_sdk_directory = FreelineUtils.joinPath(projectDescription.sdk_directory, 'platforms', projectDescription.compile_sdk_version)
        projectDescription.package = project.android.defaultConfig.applicationId.toString()
        projectDescription.debug_package = projectDescription.package
        projectDescription.main_src_directory = []
        project.android.sourceSets.main.java.srcDirs.asList().collect(projectDescription.main_src_directory) { it.absolutePath }
        projectDescription.main_res_directory = []
        project.android.sourceSets.main.res.srcDirs.asList().collect(projectDescription.main_res_directory) { it.absolutePath }
        projectDescription.main_assets_directory = []
        project.android.sourceSets.main.assets.srcDirs.asList().collect(projectDescription.main_assets_directory) { it.absolutePath }
        projectDescription.main_manifest_path = project.android.sourceSets.main.manifest.srcFile.path
        projectDescription.launcher = ''
        projectDescription.apk_path = apkPath
        projectDescription.extra_dep_res_paths = extraResourcesDependencies
        projectDescription.exclude_dep_res_paths = excludeResourceDependencyPaths
        projectDescription.main_r_path = FreelineGenerator.generateMainRPath(projectDescription.build_directory.toString(), productFlavor, projectDescription.package.toString())

        project.android.buildTypes.each { buildType ->
            if ("debug".equalsIgnoreCase(buildType.name)) {
                if (buildType.applicationIdSuffix) {
                    projectDescription.debug_package = projectDescription.package + buildType.applicationIdSuffix
                }
            }
        }

        if (apkPath == null || apkPath == '') {
            // set default build script
            projectDescription.apk_path = FreelineGenerator.generateApkPath(projectDescription.build_directory.toString(), project.name, productFlavor)
        }

        if (buildScript == null || buildScript == '') {
            // set default build script
            projectDescription.build_script = FreelineGenerator.generateBuildScript(productFlavor)
        }

        // fix debug build-type sourceSets
        if (project.android.sourceSets.debug != null) {
            appendDirs(projectDescription.main_src_directory, project.android.sourceSets.debug.java.srcDirs.asList())
            appendDirs(projectDescription.main_res_directory, project.android.sourceSets.debug.res.srcDirs.asList())
            appendDirs(projectDescription.main_assets_directory, project.android.sourceSets.debug.assets.srcDirs.asList())
        }

        projectDescription.project_source_sets = [:]
        projectDescription.modules = []
        project.rootProject.allprojects.each { pro ->
            def sourceSets = [:]
            sourceSets.main_src_directory = []
            sourceSets.main_res_directory = []
            sourceSets.main_assets_directory = []
            if (pro.hasProperty("android") && pro.android.hasProperty("sourceSets")) {
                pro.android.sourceSets.main.java.srcDirs.asList().collect(sourceSets.main_src_directory) { it.absolutePath }
                pro.android.sourceSets.main.res.srcDirs.asList().collect(sourceSets.main_res_directory) { it.absolutePath }
                pro.android.sourceSets.main.assets.srcDirs.asList().collect(sourceSets.main_assets_directory) { it.absolutePath }
                sourceSets.main_manifest_path = pro.android.sourceSets.main.manifest.srcFile.path

                appendDirs(sourceSets.main_src_directory, pro.android.sourceSets.debug.java.srcDirs.asList())
                appendDirs(sourceSets.main_res_directory, pro.android.sourceSets.debug.res.srcDirs.asList())
                appendDirs(sourceSets.main_assets_directory, pro.android.sourceSets.debug.assets.srcDirs.asList())

                projectDescription.project_source_sets[pro.name] = sourceSets
                projectDescription.modules.add(['name': pro.name, 'path': pro.projectDir.absolutePath])
            } else if (pro.plugins.hasPlugin("java") && pro.hasProperty("sourceSets")) {
                pro.sourceSets.main.allJava.srcDirs.asList().collect(sourceSets.main_src_directory) { it.absolutePath }
                sourceSets.main_manifest_path = null
                projectDescription.project_source_sets[pro.name] = sourceSets
                projectDescription.modules.add(['name': pro.name, 'path': pro.projectDir.absolutePath])
            }
        }

        // get launcher activity name
        projectDescription.launcher = FreelineParser.getLauncher(projectDescription.main_manifest_path.toString(), projectDescription.package.toString())
        // get module dependencies
        projectDescription.module_dependencies = findModuleDependencies(project)

        def json = new JsonBuilder(projectDescription).toPrettyString()
        println json

        FreelineUtils.saveJson(json, FreelineUtils.joinPath(projectDescription.freeline_cache_dir, 'project_description.json'), true)
    }

    private static boolean checkFreelineProjectDirExists(Project project) {
        String rootPath = project.rootProject.getRootDir()
        def dir = new File(rootPath, "freeline")
        return dir.exists() && dir.isDirectory()
    }

    private static String getJavaHome() {
        String env = System.getenv("JAVA_HOME")
        if (env == null || "".equals(env)) {
            env = System.getProperty("java.home").replace(File.separator + "jre", "")
        }
        return env
    }

    private static void appendDirs(def targetCollections, def collections) {
        collections.each { dir ->
            if (dir.exists() && dir.isDirectory()) {
                targetCollections.add(dir.absolutePath)
            }
        }
    }

    private static def findModuleDependencies(Project project) {
        def mappers = []
        project.rootProject.allprojects.each { p ->
            def mapper = ["match" : "", "name": p.name]
            if (p.hasProperty("android") && p.android.hasProperty("sourceSets")) {
                mapper.match = "${p.name}-release.aar"
                mappers.add(mapper)
            } else if (p.plugins.hasPlugin("java")) {
                mapper.match = "libs${File.separator}${p.name}.jar"
                mappers.add(mapper)
            }
        }

        def module_dependencies = [:]
        project.rootProject.allprojects.each { pro ->
            def deps = []
            try {
                if (pro.configurations.getByName("compile") != null) {
                    pro.configurations.compile.asPath.split(":").each { f ->
                        mappers.each { mapper ->
                            if (f.endsWith(mapper.match)) {
                                deps.add(mapper.name)
                                return false
                            }
                        }
                    }
                }
            } catch (UnknownConfigurationException e) {
                //
            }
            module_dependencies[pro.name] = deps
        }

        return module_dependencies
    }

}
