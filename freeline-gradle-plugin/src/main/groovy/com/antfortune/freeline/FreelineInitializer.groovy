package com.antfortune.freeline

import com.android.build.gradle.AppExtension
import com.antfortune.freeline.versions.StaticVersionComparator
import com.antfortune.freeline.versions.VersionParser
import groovy.json.JsonBuilder
import org.gradle.api.Project

import java.security.InvalidParameterException

/**
 * Created by huangyong on 16/7/19.
 */
class FreelineInitializer {

    public static void initFreeline(Project project) {
        println "Freeline initial process start..."
        FreelineDownloader.execute(project)
        generateProjectDescription(project)
    }

    public static void generateProjectDescription(Project project) {
        def android = project.extensions.findByName("android") as AppExtension
        def extension = project.extensions.findByName("freeline") as FreelineExtension
        def productFlavor = extension.productFlavor
        def buildScript = extension.buildScript
        def buildScriptWorkDirectory = extension.buildScriptWorkDirectory
        def apkPath = extension.apkPath
        def packageName = extension.packageName
        def launcher = extension.launcher
        def extraResourcesDependencies = extension.extraResourceDependencyPaths
        def excludeResourceDependencyPaths = extension.excludeResourceDependencyPaths
        def autoDependency = extension.autoDependency
        def ignoreResourceIds = extension.ignoreResourceIds
        def checkSourcesMd5 = extension.checkSourcesMd5
        def useSystemGradle = extension.useSystemGradle

        def projectDescription = [:]

        projectDescription.project_type = 'gradle'
        projectDescription.android_gradle_plugin_version = FreelineUtils.getAndroidGradlePluginVersion(project)
        projectDescription.freeline_gradle_plugin_version = FreelineUtils.getFreelineGradlePluginVersion(project)
        projectDescription.java_home = getJavaHome()
        projectDescription.freeline_cache_dir = FreelineUtils.getFreelineCacheDir(project.rootDir.absolutePath)
        projectDescription.auto_dependency = autoDependency
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
        projectDescription.package = packageName  // package -> the package name in the manifest file of main module
        projectDescription.debug_package = project.android.defaultConfig.applicationId
        // applicationId -> debug_package
        projectDescription.main_manifest_path = android.sourceSets.main.manifest.srcFile.path
        projectDescription.launcher = launcher
        projectDescription.apk_path = apkPath
        projectDescription.extra_dep_res_paths = extraResourcesDependencies
        projectDescription.exclude_dep_res_paths = excludeResourceDependencyPaths
        projectDescription.main_r_path = FreelineGenerator.generateMainRPath(projectDescription.build_directory.toString(), productFlavor, projectDescription.package.toString())
        projectDescription.use_jdk8 = isUseJdk8(projectDescription.android_gradle_plugin_version as String)
        projectDescription.ignore_resource_ids = FreelineCompat.compatIgnoreResourceIds(ignoreResourceIds)
        projectDescription.use_system_gradle = useSystemGradle

        def useMd5PathArray = [];
        for (String path : checkSourcesMd5) {
            useMd5PathArray.add(FreelineUtils.joinPath(project.rootDir.absolutePath, path))
        }
        projectDescription.check_sources_md5 = useMd5PathArray

        if (FreelineUtils.isEmpty(packageName)) {
            projectDescription.package = FreelineParser.getPackage(projectDescription.main_manifest_path as String)
            if (FreelineUtils.isEmpty(projectDescription.package as String)) {
                projectDescription.package = projectDescription.debug_package
            }
        }

        if (FreelineUtils.isEmpty(projectDescription.debug_package as String)) {
            projectDescription.debug_package = projectDescription.package
        }

        boolean invalidFlavor = true;
        if (!productFlavor) {
            invalidFlavor = false;
        }

        boolean applicationSuffixAdded = false
        android.applicationVariants.each { baseVariant ->
            if (productFlavor) {
                if (productFlavor.equals(baseVariant.flavorName)) {
                    invalidFlavor = false;
                }
            }
            def buildType = baseVariant.buildType;
            if (!applicationSuffixAdded && "debug".equalsIgnoreCase(buildType.name as String)) {
                if (buildType.applicationIdSuffix) {
                    projectDescription.debug_package = projectDescription.debug_package + buildType.applicationIdSuffix
                    applicationSuffixAdded = true
                }
            }
        }

        if (invalidFlavor) {
            throw new InvalidParameterException(" invalid productFlavor : ${productFlavor}");
        }

        if (apkPath == null || apkPath == '') {
            // set default build script
            projectDescription.apk_path = FreelineGenerator.generateApkPath(projectDescription.build_directory.toString(), project.name, productFlavor)
        }

        if (buildScript == null || buildScript == '') {
            // set default build script
            def isRootModuleTheMainModule = project.rootProject.name == projectDescription.main_project_name
            projectDescription.build_script = FreelineGenerator.generateBuildScript(isRootModuleTheMainModule, projectDescription.main_project_name as String, productFlavor)
        }

        if (launcher == null || launcher == '') {
            // get launcher activity name
            projectDescription.launcher = FreelineParser.getLauncher(projectDescription.main_manifest_path.toString(), projectDescription.package.toString())
        }

        Map<String, Project> allProjectMap = new HashMap<>();
        def rootProject = project.rootProject;
        rootProject.allprojects {
            allProjectMap.put(it.name, it);
        }
        // get module dependencies
        def moduleDependencies = findModuleDependencies(project, allProjectMap, productFlavor);

        def module_dependencies = [:]
        moduleDependencies.keySet().findAll { projectName ->
            def deps = [];
            module_dependencies[projectName] = deps
            moduleDependencies.get(projectName).findAll { dependency ->
                deps.add(dependency.name);
            }
        }

        def allProjectProductInfoMap = [:];
        allProjectProductInfoMap[project.name] = new ProjectProductInfo("android", project.name, productFlavor, "debug");
        moduleDependencies.keySet().findAll { projectName ->
            moduleDependencies.get(projectName).findAll { dependency ->
                allProjectProductInfoMap[dependency.name] = dependency;
            }
        }
        def project_source_sets = [:]
        def modules = []
        allProjectProductInfoMap.values().findAll { product ->
            def pro = allProjectMap.get(product.name)
            def sourceSets = createSourceSets(pro, product.flavor, product.buildType)
            project_source_sets[pro.name] = sourceSets
            modules.add(['name': pro.name, 'path': pro.projectDir.absolutePath])
        }

        def mainAppSourceSets = project_source_sets[project.name];
        projectDescription.main_src_directory = mainAppSourceSets.main_src_directory;
        projectDescription.main_res_directory = mainAppSourceSets.main_res_directory;
        projectDescription.main_assets_directory = mainAppSourceSets.main_assets_directory;
        projectDescription.main_jni_directory = mainAppSourceSets.main_jni_directory;
        projectDescription.main_jniLibs_directory = mainAppSourceSets.main_jniLibs_directory;
        projectDescription.project_source_sets = project_source_sets

        projectDescription.modules = modules
        projectDescription.module_dependencies = module_dependencies

        projectDescription.databinding = []
        projectDescription.databinding_modules = []
        project.rootProject.allprojects.each { pro ->
            if (pro.plugins.hasPlugin("com.android.application") || pro.plugins.hasPlugin("com.android.library")) {
                if (pro.android.hasProperty("dataBinding")
                        && pro.android.dataBinding.enabled
                        && projectDescription.project_source_sets[pro.name] != null) {
                    def data = [:]
                    String manifestPath = projectDescription.project_source_sets[pro.name].main_manifest_path
                    data.name = pro.name
                    data.isLibrary = pro.plugins.hasPlugin("com.android.library")
                    data.minSdkVersion = getMinSdkVersion(pro, manifestPath)
                    if (pro.name == project.name) {
                        data.packageName = projectDescription.package
                    } else if (data.isLibrary) {
                        data.packageName = FreelineParser.getPackage(manifestPath)
                    }
                    projectDescription.databinding.add(data)
                    projectDescription.databinding_modules.add(pro.name)
                }
            }
        }

        def json = new JsonBuilder(projectDescription).toPrettyString()
        println json

        FreelineUtils.saveJson(json, FreelineUtils.joinPath(projectDescription.freeline_cache_dir, Constants.FREELINE_PRO_DESC_FILE_NAME), true)
    }

    private static def getMinSdkVersion(Project project, String manifestPath) {
        if (project.android.defaultConfig.minSdkVersion != null) {
            return project.android.defaultConfig.minSdkVersion.apiLevel
        } else {
            return FreelineParser.getMinSdkVersion(manifestPath)
        }
    }

    private static boolean isUseJdk8(String androidGradleVersion) {
        if (FreelineUtils.isEmpty(androidGradleVersion)) {
            return false
        }
        // Use custom class to avoid java.lang.NoClassDefFoundError in lower gradle version.
        VersionParser versionParser = new VersionParser()
        int result = new StaticVersionComparator().compare(versionParser.transform(androidGradleVersion),
                versionParser.transform("2.2.0"))
        return result >= 0
    }

    private static def createSourceSets(Project pro, def flavor, def buildType) {
        def sourceSets = [:]
        sourceSets.main_src_directory = []
        sourceSets.main_res_directory = []
        sourceSets.main_assets_directory = []
        sourceSets.main_jni_directory = []
        sourceSets.main_jniLibs_directory = []
        if (pro.hasProperty("android") && pro.android.hasProperty("sourceSets")) {
            if (flavor && buildType) {
                collectSourceSet(pro, sourceSets, flavor + buildType.capitalize() as String);
            }
            if (buildType) {
                collectSourceSet(pro, sourceSets, buildType as String)
            }
            if (flavor) {
                collectSourceSet(pro, sourceSets, flavor as String)
            }
            collectSourceSet(pro, sourceSets, "main")
            sourceSets.main_manifest_path = pro.android.sourceSets.main.manifest.srcFile.path
            return sourceSets;
        } else if (pro.plugins.hasPlugin("java") && pro.hasProperty("sourceSets")) {
            pro.sourceSets.main.allJava.srcDirs.asList().collect(sourceSets.main_src_directory) {
                it.absolutePath
            }
            sourceSets.main_manifest_path = null
            return sourceSets;
        }
    }

    private
    static void collectSourceSet(Project pro, LinkedHashMap sourceSets, String sourceSetKey) {
        def sourceSetsValue = pro.android.sourceSets.findByName(sourceSetKey);
        if (sourceSetsValue) {
            appendDirs(sourceSets.main_src_directory, sourceSetsValue.java.srcDirs.asList())
            appendDirs(sourceSets.main_res_directory, sourceSetsValue.res.srcDirs.asList())
            appendDirs(sourceSets.main_assets_directory, sourceSetsValue.assets.srcDirs.asList())
            appendDirs(sourceSets.main_jni_directory, sourceSetsValue.jni.srcDirs.asList())
            appendDirs(sourceSets.main_jniLibs_directory, sourceSetsValue.jniLibs.srcDirs.asList())
        }
    }


    private static boolean checkFreelineProjectDirExists(Project project) {
        String rootPath = project.rootProject.getRootDir()
        def dir = new File(rootPath, "freeline")
        return dir.exists() && dir.isDirectory()
    }

    // from retrolambda
    public static String getJavaHome() {
        String javaHomeProp = System.properties.'java.home'
        if (javaHomeProp) {
            int jreIndex = javaHomeProp.lastIndexOf("${File.separator}jre")
            if (jreIndex != -1) {
                return javaHomeProp.substring(0, jreIndex)
            } else {
                return javaHomeProp
            }
        } else {
            return System.getenv("JAVA_HOME")
        }
    }

    private static void appendDirs(def targetCollections, def collections) {
        collections.each { dir ->
            targetCollections.add(dir.absolutePath)
        }
    }

    private static
    def findModuleDependencies(Project project, Map<String, Project> allProjectMap, String productFlavor) {
        def moduleDependencies = [:]
        handleAndroidProject(project, allProjectMap, productFlavor, "debug", moduleDependencies);
        return moduleDependencies
    }

    private
    static void handleAndroidProject(Project project, Map<String, Project> allProjectMap, String productFlavor, String buildType,
                                     def moduleDependencies) {
        def deps = [];
        moduleDependencies[project.name] = deps
        def compile = project.configurations.findByName("compile");
        if (compile) {
            collectLocalDependency(allProjectMap, compile, moduleDependencies, deps)
        }

        def implementation = project.configurations.findByName("implementation")
        if (implementation) {
            collectLocalDependency(allProjectMap, implementation, moduleDependencies, deps)
        }

        def api = project.configurations.findByName("api")
        if (api) {
            collectLocalDependency(allProjectMap, api, moduleDependencies, deps)
        }

        if (productFlavor) {
            def productFlavorCompile = project.configurations.findByName(productFlavor + "Compile");
            if (productFlavorCompile) {
                collectLocalDependency(allProjectMap, productFlavorCompile, moduleDependencies, deps)
            }

            def productFlavorDebugCompile = project.configurations.findByName(productFlavor + buildType.capitalize() + "Compile");
            if (productFlavorDebugCompile) {
                collectLocalDependency(allProjectMap, productFlavorDebugCompile, moduleDependencies, deps)
            }
        }
        def debugCompile = project.configurations.findByName(buildType + "Compile");
        if (debugCompile) {
            collectLocalDependency(allProjectMap, debugCompile, moduleDependencies, deps)
        }
    }


    private static void handleJavaProject(Project project, Map<String, Project> allProjectMap,
                                          def moduleDependencies) {
        def deps = [];
        moduleDependencies[project.name] = deps
        def compile = project.configurations.findByName("compile");
        if (compile) {
            collectLocalDependency(allProjectMap, compile, moduleDependencies, deps)
        }
    }

    private static void collectLocalDependency(Map<String, Project> allProjectMap,
                                               def xxxCompile, def moduleDependencies, def deps) {
        xxxCompile.dependencies.findAll { dependency ->
            if (dependency.hasProperty('dependencyProject')) {
                handleDependency(allProjectMap, dependency, moduleDependencies, deps)
            }
        }
    }

    private static void handleDependency(Map<String, Project> allProjectMap,
                                         def dependency, def moduleDependencies, def deps) {
        Project dependencyProject = allProjectMap.get(dependency.name);
        if (dependencyProject != null) {
            if (dependencyProject.plugins.hasPlugin("com.android.library")) {
                handleAndroidDependency(dependencyProject, allProjectMap, dependency, moduleDependencies, deps)
            } else if (dependencyProject.plugins.hasPlugin("java")) {
                handleJavaDependency(dependencyProject, allProjectMap, dependency, moduleDependencies, deps)
            }
        }
    }

    private static void handleJavaDependency(Project dependencyProject, Map allProjectMap,
                                             def dependency, def moduleDependencies, def deps) {
        deps.add(new ProjectProductInfo("java", dependencyProject.name, null, null))
        handleJavaProject(dependencyProject, allProjectMap, moduleDependencies)
    }

    private static void handleAndroidDependency(Project dependencyProject, Map allProjectMap,
                                                def dependency, def moduleDependencies, def deps) {
        def configuration = dependency.properties.get("configuration");
        String favorBuildType = "release";
        if (configuration != null && !configuration.equals("default")) {
            favorBuildType = configuration;
        } else {
            if (dependencyProject.hasProperty("android")) {
                def android = dependencyProject.properties.get("android");
                if (android.hasProperty("defaultPublishConfig")) {
                    favorBuildType = android.properties.get("defaultPublishConfig");
                }
            }
        }
        def android = dependencyProject.properties.get("android");
        if (android != null && android.hasProperty("libraryVariants")) {
            android.libraryVariants.each { bv ->
                if (bv.getName().equalsIgnoreCase(favorBuildType)) {
                    deps.add(new ProjectProductInfo("android", dependencyProject.name, bv.flavorName, bv.buildType.name))
                    handleAndroidProject(dependencyProject, allProjectMap, bv.flavorName as String, bv.buildType.name as String, moduleDependencies);
                    return;
                }
            }
        }

    }

    public static class ProjectProductInfo {

        String projectType;
        String name;
        String flavor;
        String buildType;

        ProjectProductInfo(String projectType, String name, String flavor, String buildType) {
            this.projectType = projectType
            this.name = name
            this.flavor = flavor
            this.buildType = buildType
        }

        @Override
        public String toString() {
            return "ProjectProductInfo{" +
                    "projectType='" + projectType + '\'' +
                    ", name='" + name + '\'' +
                    ", flavor='" + flavor + '\'' +
                    ", buildType='" + buildType + '\'' +
                    '}';
        }
    }

}
