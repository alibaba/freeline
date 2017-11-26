package com.antfortune.freeline

import com.android.build.gradle.AppExtension
import com.android.utils.FileUtils
import groovy.io.FileType
import groovy.json.JsonBuilder
import groovy.xml.XmlUtil
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.util.VersionNumber

/**
 * Created by yeqi on 16/5/3.
 */
class FreelinePlugin implements Plugin<Project> {
    private Project project
    private AppExtension androidExt
    private FreelineExtension freelineExt

    @Override
    void apply(Project project) {
        // check
        if (!project.plugins.hasPlugin("com.android.application")) {
            throw new RuntimeException("Freeline plugin can only be applied for android application module.")
        }
        this.project = project
        this.androidExt = project.extensions.getByName("android") as AppExtension
        this.freelineExt = project.extensions.create("freeline", FreelineExtension, project)

        project.rootProject.task("initFreeline").doLast {
            FreelineInitializer.initFreeline(project)
        }

        project.rootProject.task("checkBeforeCleanBuild").doLast {
            FreelineInitializer.generateProjectDescription(project)
        }

        project.afterEvaluate {
            autoDependency()

            androidExt.applicationVariants.each { variant ->
                def extension = freelineExt
                def productFlavor = extension.productFlavor
                def apkPath = extension.apkPath
                def excludeHackClasses = extension.excludeHackClasses
                def forceLowerVersion = extension.foceLowerVersion
                def applicationProxy = extension.applicationProxy
                def aptEnabled = extension.aptEnabled
                def retrolambdaEnabled = extension.retrolambdaEnabled
                def forceVersionName = extension.forceVersionName
                def freelineBuild = FreelineUtils.getProperty(project, "freelineBuild")

                // check match freeline condition
                if (!freelineBuild) {
                    return
                }

                //早点判断Android Studio的plugin版本
                def isLowerVersion = false
                def isStudioCanaryVersion = false //是不是Android studio3.0的plugin
                if (!forceLowerVersion) {
                    project.rootProject.buildscript.configurations.classpath.resolvedConfiguration.firstLevelModuleDependencies.each {
                        if (it.moduleGroup == "com.android.tools.build" && it.moduleName == "gradle") {
                            if (!it.moduleVersion.startsWith("1.5")
                                    && !it.moduleVersion.startsWith("2") && !it.moduleVersion.startsWith("3")) {
                                isLowerVersion = true
                                return false
                            } else if (it.moduleVersion.startsWith("3")) {
                                isStudioCanaryVersion = true
                            }
                        }
                    }
                } else {
                    isLowerVersion = true
                }

                if (!"debug".equalsIgnoreCase(variant.buildType.name as String)) {
                    println "variant ${variant.name} is not debug, skip hack process."
                    return
                } else if (!FreelineUtils.isEmpty(productFlavor) && !productFlavor.toString().equalsIgnoreCase(variant.flavorName)) {
                    println "variant ${variant.name} is not ${productFlavor}, skip hack process."
                    return
                }

                println "find variant ${variant.name} start hack process..."

                if (forceVersionName) {
                    variant.mergedFlavor.versionName = "FREELINE"
                }

                if (isProguardEnable(project, variant)) {
                    throw new RuntimeException("Freeline doesn't support proguard now, please disable proguard config then re-run freeline.")
                }

                // find the default apk
                if (FreelineUtils.isEmpty(apkPath)) {
                    def apk_paths = []
                    variant.outputs.each { output ->
                        def path = output.outputFile.absolutePath
                        if (path.endsWith(".apk")) {
                            apk_paths.add(output.outputFile.absolutePath)
                        }
                    }
                    def defaultApkPath = FreelineUtils.getDefaultApkPath(apk_paths, project.buildDir.absolutePath, project.name, productFlavor)
                    println "find default apk path: ${defaultApkPath}"
                    FreelineUtils.addNewAttribute(project, 'apk_path', defaultApkPath)
                }

                // find the correct application id
                def mergedApplicationId = variant.mergedFlavor.applicationId
                if (mergedApplicationId) {
                    def description = FreelineUtils.readProjectDescription(project)
                    if (mergedApplicationId != description['debug_package']) {
                        println "find new application id: ${mergedApplicationId}"
                        //FreelineUtils.addNewAttribute(project, "debug_package", mergedApplicationId)
                        if (variant.buildType.applicationIdSuffix) {
                            FreelineUtils.addNewAttribute(project, "debug_package", mergedApplicationId + variant.buildType.applicationIdSuffix)
                        } else {
                            FreelineUtils.addNewAttribute(project, "debug_package", mergedApplicationId)
                        }
                    }
                }

                // add addtional aapt args
                def publicKeeperGenPath = FreelineUtils.joinPath(FreelineUtils.getBuildCacheDir(project.buildDir.absolutePath), "public_keeper.xml")
                project.android.aaptOptions.additionalParameters("-P", publicKeeperGenPath)
                println "Freeline add additionalParameters `-P ${publicKeeperGenPath}` to aaptOptions"

                // add generate task
                FreelineConfigGenerateTask generateTask = project.tasks.create("generate${variant.name.capitalize()}FreelineConfig", FreelineConfigGenerateTask)
                def freelineGenerateOutputDir = new File("$project.buildDir/generated/freeline")
                def manifestPath = project.android.sourceSets.main.manifest.srcFile.path
                generateTask.packageName = FreelineParser.getPackageName(project.android.defaultConfig.applicationId.toString(), manifestPath)
                generateTask.applicationClass = FreelineParser.getApplication(manifestPath, generateTask.packageName)
                generateTask.outputDir = freelineGenerateOutputDir
                variant.registerJavaGeneratingTask(generateTask, freelineGenerateOutputDir)

                // force tasks to run
                def mergeAssetsTask = project.tasks.findByName("merge${variant.name.capitalize()}Assets")
                mergeAssetsTask.outputs.upToDateWhen { false }

                if (applicationProxy) {
                    variant.outputs.each { output ->
                        output.processManifest.outputs.upToDateWhen { false }
                        output.processManifest.doLast {
                            File manifestFile
                            if (isStudioCanaryVersion) {
                                manifestFile = new File("$manifestOutputDirectory/AndroidManifest.xml")
                            } else {
                                manifestFile = output.processManifest.manifestOutputFile
                            }

                            if (manifestFile != null && manifestFile.exists()) {
                                println "find manifest file path: ${manifestFile.absolutePath}"
                                replaceApplication(manifestFile.absolutePath as String)
                            }
                        }
                    }
                }

                // add freeline generated files to assets
                mergeAssetsTask.doLast {
                    addFreelineGeneratedFiles(project, new File(FreelineGenerator.generateProjectBuildAssetsPath(project.buildDir.absolutePath, productFlavor)), null)
                }

                // find thrid party libraries' resources dependencies
                project.rootProject.allprojects.each { p ->
                    findResourceDependencies(variant, p, FreelineUtils.getBuildCacheDir(project.buildDir.absolutePath), "resources")
                    findResourceDependencies(variant, p, FreelineUtils.getBuildCacheDir(project.buildDir.absolutePath), "assets")
                }

                def addtionalJars = []
                def projectAptConfig = [:]
                def projectRetrolambdaConfig = [:]
                def aptLibraries = ['dagger': false, 'butterknife': false]

                project.rootProject.allprojects.each { pro ->
                    if (pro.plugins.hasPlugin("com.android.application") || pro.plugins.hasPlugin("com.android.library")) {
                        // find additional jars
                        if (pro.android.hasProperty("libraryRequests")) {
                            pro.android.libraryRequests.each { p ->
                                def jar_path = FreelineUtils.joinPath(
                                        pro.android.sdkDirectory.toString(),
                                        'platforms',
                                        pro.android.compileSdkVersion.toString(),
                                        'optional',
                                        p.name + ".jar")
                                def f = new File(jar_path)
                                if (f.exists() && f.isFile()) {
                                    addtionalJars.add(jar_path)
                                    println "find additional jar: ${jar_path}"
                                }
                            }
                        }

                        // find apt config
                        findAptConfig(pro, variant, projectAptConfig)
                    }

                    // find retrolambda config
                    if (retrolambdaEnabled && pro.plugins.hasPlugin("me.tatarka.retrolambda")) {
                        def jdk8 = getJdk8()
                        if (jdk8 != null) {
                            def rtJar = "${jdk8}/jre/lib/rt.jar"
                            def retrolambdaConfig = pro.configurations.getByName("retrolambdaConfig")
                            def targetJar = pro.files(retrolambdaConfig).asPath
                            def mainClass = 'net.orfjackal.retrolambda.Main'

                            VersionNumber retrolambdaVersion = retrolambdaVersion(retrolambdaConfig)
                            def supportIncludeFiles = requireVersion(retrolambdaVersion, '2.1.0')

                            def lambdaConfig = [
                                    'enabled'            : retrolambdaEnabled,
                                    'targetJar'          : targetJar,
                                    'mainClass'          : mainClass,
                                    'rtJar'              : rtJar,
                                    'supportIncludeFiles': supportIncludeFiles
                            ]
                            projectRetrolambdaConfig[pro.name] = lambdaConfig
                        } else {
                            println '[WARNING] JDK8 not found, skip retrolambda.'
                        }
                    }

                    if (aptEnabled) {
                        if (pro.configurations.findByName("compile") != null) {
                            pro.configurations.compile.resolvedConfiguration.firstLevelModuleDependencies.each {
                                if (it.moduleGroup == 'com.google.dagger'
                                        || it.moduleGroup == 'com.squareup.dagger') {
                                    aptLibraries.dagger = true
                                } else if (it.moduleGroup == 'com.jakewharton' && it.moduleName == 'butterknife') {
                                    aptLibraries.butterknife = true
                                }
                            }
                        }
                    }
                }

                // find databinding compiler jar path
                def databindingCompilerJarPath = ""
                if (project.android.hasProperty("dataBinding") && project.android.dataBinding.enabled) {
                    println "[FREELINE] dataBinding enabled."
                    def javaCompileTask = getJavaCompileTask(variant, project)
                    if (javaCompileTask) {
                        javaCompileTask.doFirst {
                            int processorIndex = javaCompileTask.options.compilerArgs.indexOf('-processorpath')
                            if (processorIndex != -1) {
                                def processor = javaCompileTask.options.compilerArgs.get(processorIndex + 1)
                                processor.split(File.pathSeparator).each { jarPath ->
                                    if (jarPath.contains("com.android.databinding${File.separator}compiler")) {
                                        println "find dataBinding compiler jar path: ${jarPath}"
                                        databindingCompilerJarPath = jarPath
                                        return false
                                    }
                                }
                            }
                        }
                    }
                }

                // modify .class file
                def classesProcessTask
                def preDexTask
                def multiDexListTask

                boolean multiDexEnabled
                if (isStudioCanaryVersion) {
//                因为gradle plugin最新版的variantData命名和之前相比不同
                    multiDexEnabled = variant.variantData.variantConfiguration.isMultiDexEnabled()
                } else {
                    multiDexEnabled = variant.apkVariantData.variantConfiguration.isMultiDexEnabled()
                }

                if (isLowerVersion) {
                    if (multiDexEnabled) {
                        classesProcessTask = project.tasks.findByName("packageAll${variant.name.capitalize()}ClassesForMultiDex")
                        multiDexListTask = project.tasks.findByName("create${variant.name.capitalize()}MainDexClassList")
                    } else {
                        classesProcessTask = project.tasks.findByName("dex${variant.name.capitalize()}")
                        preDexTask = project.tasks.findByName("preDex${variant.name.capitalize()}")
                    }
                } else if (isStudioCanaryVersion) {
                    classesProcessTask = project.tasks.findByName("transformClassesWithDexBuilderFor${variant.name.capitalize()}")
                    String manifest_path = project.android.sourceSets.main.manifest.srcFile.path
                    if (getMinSdkVersion(variant.mergedFlavor, manifest_path) < 21 && multiDexEnabled) {
                        //classProcesstask没变
                        multiDexListTask = project.tasks.findByName("transformClassesWithMultidexlistFor${variant.name.capitalize()}")
                    }
                } else {
                    String manifest_path = project.android.sourceSets.main.manifest.srcFile.path
                    if (getMinSdkVersion(variant.mergedFlavor, manifest_path) < 21 && multiDexEnabled) {
                        classesProcessTask = project.tasks.findByName("transformClassesWithJarMergingFor${variant.name.capitalize()}")
                        multiDexListTask = project.tasks.findByName("transformClassesWithMultidexlistFor${variant.name.capitalize()}")
                    } else {
                        classesProcessTask = project.tasks.findByName("transformClassesWithDexFor${variant.name.capitalize()}")
                    }
                }

                if (classesProcessTask == null) {
                    println "Can not find ClassProcess Task ,Skip ${project.name}'s hack process"
                    return
                }

                classesProcessTask.outputs.upToDateWhen { false }
                String backUpDirPath = FreelineUtils.getBuildBackupDir(project.buildDir.absolutePath)
                def modules = [:]
                project.rootProject.allprojects.each { pro ->
                    //modules.add("exploded-aar" + File.separator + pro.group + File.separator + pro.name + File.separator)
                    //modules[pro.name] = "exploded-aar" + File.separator + pro.group + File.separator + pro.name + File.separator
                    modules[pro.name] = [
                            "exploded-aar${File.separator}${pro.group}${File.separator}${pro.name}${File.separator}",
                            "${pro.name}${File.separator}build${File.separator}intermediates${File.separator}bundles${File.separator}"
                    ]
                }

                if (preDexTask) {
                    preDexTask.outputs.upToDateWhen { false }
                    def hackClassesBeforePreDex = "hackClassesBeforePreDex${variant.name.capitalize()}"
                    project.task(hackClassesBeforePreDex) << {
                        def jarDependencies = []

                        preDexTask.inputs.files.files.each { f ->
                            if (f.path.endsWith(".jar")) {
                                // 屏蔽注入，减少Windows IO操作
                                //FreelineInjector.inject(excludeHackClasses, f as File, modules.values())
                                jarDependencies.add(f.path)
                            }
                        }
                        def json = new JsonBuilder(jarDependencies).toPrettyString()
                        project.logger.info(json)
                        FreelineUtils.saveJson(json, FreelineUtils.joinPath(FreelineUtils.getBuildCacheDir(project.buildDir.absolutePath), "jar_dependencies.json"), true);
                    }

                    def hackClassesBeforePreDexTask = project.tasks[hackClassesBeforePreDex]
                    hackClassesBeforePreDexTask.dependsOn preDexTask.taskDependencies.getDependencies(preDexTask)
                    preDexTask.dependsOn hackClassesBeforePreDexTask
                }

                def hackClassesBeforeDex = "hackClassesBeforeDex${variant.name.capitalize()}"
                def backupMap = [:]
                project.task(hackClassesBeforeDex) << {
                    def jarDependencies = []
                    classesProcessTask.inputs.files.files.each { f ->
                        if (f.isDirectory()) {
                            f.eachFileRecurse(FileType.FILES) { file ->
                                // 屏蔽注入，减少Windows IO操作
                                //backUpClass(backupMap, file as File, backUpDirPath as String, modules.values())
                                //FreelineInjector.inject(excludeHackClasses, file as File, modules.values())
                                if (file.path.endsWith(".jar")) {
                                    jarDependencies.add(file.path)
                                }
                            }
                        } else {
                            // 屏蔽注入，减少Windows IO操作
                            //backUpClass(backupMap, f as File, backUpDirPath as String, modules.values())
                            //FreelineInjector.inject(excludeHackClasses, f as File, modules.values())
                            if (f.path.endsWith(".jar")) {
                                jarDependencies.add(f.path)
                            }
                        }
                    }

                    if (preDexTask == null) {
                        def providedConf = project.configurations.findByName("provided")
//                        providedConf.setCanBeResolved(true) //适配3.0 但是这里不行
                        if (providedConf) {
                            def providedJars = providedConf.asPath.split(File.pathSeparator)
                            jarDependencies.addAll(providedJars)
                        }

                        jarDependencies.addAll(addtionalJars)
                        // add all additional jars to final jar dependencies
                        def json = new JsonBuilder(jarDependencies).toPrettyString()
                        project.logger.info(json)
                        FreelineUtils.saveJson(json, FreelineUtils.joinPath(FreelineUtils.getBuildCacheDir(project.buildDir.absolutePath), "jar_dependencies.json"), true);
                    }
                }

                if (classesProcessTask) {
                    def hackClassesBeforeDexTask = project.tasks[hackClassesBeforeDex]
                    hackClassesBeforeDexTask.dependsOn classesProcessTask.taskDependencies.getDependencies(classesProcessTask)
                    classesProcessTask.dependsOn hackClassesBeforeDexTask
                }

                if (multiDexEnabled && applicationProxy) {
                    def manifestKeepFile = new File("${project.buildDir}/intermediates/multi-dex/${variant.dirName}/manifest_keep.txt")
                    if (multiDexListTask) {
                        multiDexListTask.outputs.upToDateWhen { false }
                        multiDexListTask.doFirst {
                            manifestKeepFile << "-keep class com.antfortune.freeline.** { *; }"
                        }
                    }
                }

                def assembleTask = project.tasks.findByName("assemble${variant.name.capitalize()}")
                if (assembleTask) {
                    assembleTask.doLast {
                        FreelineAnnotationCollector.saveCollections(project, FreelineUtils.getBuildCacheDir(project.buildDir.absolutePath), modules)
                        FreelineUtils.addNewAttribute(project, 'apt', projectAptConfig)
                        FreelineUtils.addNewAttribute(project, 'retrolambda', projectRetrolambdaConfig)
                        FreelineUtils.addNewAttribute(project, 'databinding_compiler_jar', databindingCompilerJarPath)
                        FreelineUtils.addNewAttribute(project, 'apt_libraries', aptLibraries)
                        // 屏蔽注入，减少Windows IO操作
                        //rollBackClasses(backupMap)
                    }
                }
            }
        }
    }

    private void autoDependency() {
        if (freelineExt.autoDependency) {
            // the same as plugin version
            String freelineVersion = FreelineUtils.getFreelineGradlePluginVersion(project)
            println "freeline auto add runtime dependencies: ${freelineVersion}"
            project.dependencies {
                debugImplementation "com.antfortune.freeline:runtime:${freelineVersion}"
                releaseImplementation "com.antfortune.freeline:runtime-no-op:${freelineVersion}"
                testImplementation "com.antfortune.freeline:runtime-no-op:${freelineVersion}"
            }
        } else {
            println "freeline auto-dependency disabled"
        }
    }

    private static void addFreelineGeneratedFiles(Project project, File targetDir, File dirEntry) {
        if (dirEntry == null) {
            dirEntry = new File(FreelineUtils.getBuildAssetsDir(project.buildDir.absolutePath))
        }
        if (dirEntry.exists() && dirEntry.isDirectory()) {
            dirEntry.eachFileRecurse(FileType.FILES) { f ->
                def target = new File(targetDir, f.name)
                if (target.exists()) {
                    target.delete()
                } else {
                    throw new GradleException("${target.absolutePath} file not found. \nMissing the `productFlavor` configuration?\nYou can try to add `productFlavor` to freeline DSL, for example: \n\n  freeline { \n      hack true \n      productFlavor 'your-flavor' \n  }\n\nThen re-run `python freeline.py` again.\n")
                }
                target << f.text
            }
        }
    }

    private static void findResourceDependencies(
            def variant, Project project, String buildCacheDir, String type) {
        def mergeResourcesTask = project.tasks.findByName("merge${variant.name.capitalize()}${type.capitalize()}")
        def resourcesInterceptor = "${type}InterceptorBeforeMerge${variant.name.capitalize()}${type.capitalize()}"
        if (mergeResourcesTask == null) {
            mergeResourcesTask = project.tasks.findByName("mergeRelease${type.capitalize()}")
        }
        if (mergeResourcesTask == null) {
            mergeResourcesTask = project.tasks.findByName("mergeDebug${type.capitalize()}")
        }
        if (mergeResourcesTask == null) {
            println "${project.name} merge ${type} task not found."
            if (!project.hasProperty("android")) {
                return
            }

            if (type == 'resources') {
                def resourcesDependencies = ["local_resources": [], "library_resources": []]
                def searchDirs = [new File(project.buildDir, 'generated/res/resValues/release'),
                                  new File(project.buildDir, 'generated/res/rs/release')]
                searchDirs.each { dir ->
                    if (dir.exists()) {
                        resourcesDependencies.local_resources.add(dir.absolutePath)
                        println "add local resource: ${dir.absolutePath}"
                    }
                }
                def json = new JsonBuilder(resourcesDependencies).toPrettyString()
                def cacheDir = new File(FreelineUtils.joinPath(FreelineUtils.joinPath(buildCacheDir, project.name)))
                if (!cacheDir.exists()) {
                    cacheDir.mkdirs()
                }
                FreelineUtils.saveJson(json, FreelineUtils.joinPath(cacheDir.absolutePath, "resources_dependencies.json"), true)
            }
            return
        }

        project.task(resourcesInterceptor) << {
            def dir = new File(FreelineUtils.joinPath(buildCacheDir, project.name))
            if (!dir.exists()) {
                dir.mkdirs()
            }

            def resourcesDependencies = ["local_resources": [], "library_resources": []]
            def mappers = []

            project.rootProject.allprojects.each { p ->
                if (p.hasProperty("android") && p.android.hasProperty("sourceSets")) {
                    def mapper = ["match": "", "path": []]
                    mapper.match = [
                            "exploded-aar${File.separator}${p.group}${File.separator}${p.name}${File.separator}",
                            "${p.name}${File.separator}build${File.separator}intermediates${File.separator}bundles${File.separator}"
                    ]
                    if (type == "resources") {
                        p.android.sourceSets.main.res.srcDirs.asList().collect(mapper.path) {
                            it.absolutePath
                        }
                    } else if (type == "assets") {
                        p.android.sourceSets.main.assets.srcDirs.asList().collect(mapper.path) {
                            it.absolutePath
                        }
                    }
                    mappers.add(mapper)
                }
            }

            def projectResDirs = []
            if (type == "resources") {
                project.android.sourceSets.main.res.srcDirs.asList().collect(projectResDirs) {
                    it.absolutePath
                }
            } else if (type == "assets") {
                project.android.sourceSets.main.assets.srcDirs.asList().collect(projectResDirs) {
                    it.absolutePath
                }
            }

            mergeResourcesTask.inputs.files.files.each { f ->
                if (f.exists() && f.isDirectory()) {
                    def path = f.absolutePath
                    println "find resource path: ${path}"
                    if (path.contains("exploded-aar") || path.contains("build-cache") || path.contains("intermediates")) {
                        def marker = false
                        mappers.each { mapper ->
                            mapper.match.each { matcher ->
                                if (path.contains(matcher as String)) {
                                    mapper.path.collect(resourcesDependencies.local_resources) {
                                        it
                                    }
                                    println "add local resource: ${path}"
                                    marker = true
                                    return false
                                }
                            }
                        }
                        if (!marker) {
                            resourcesDependencies.library_resources.add(path)
                            println "add library resource: ${path}"
                        }
                    } else {
                        if (!projectResDirs.contains(path)) {
                            resourcesDependencies.local_resources.add(path)
                            println "add local resource: ${path}"
                        }
                    }
                }
            }

            def json = new JsonBuilder(resourcesDependencies).toPrettyString()
            project.logger.info(json)
            FreelineUtils.saveJson(json, FreelineUtils.joinPath(dir.absolutePath, "${type}_dependencies.json"), true);
        }

        def resourcesInterceptorTask = project.tasks[resourcesInterceptor]
        resourcesInterceptorTask.dependsOn mergeResourcesTask.taskDependencies.getDependencies(mergeResourcesTask)
        mergeResourcesTask.dependsOn resourcesInterceptorTask
    }

    private static void replaceApplication(String manifestPath) {
        def manifestFile = new File(manifestPath)
        def manifest = new XmlSlurper(false, false).parse(manifestFile)
        manifest.application."@android:name" = "com.antfortune.freeline.FreelineApplication"

        manifestFile.delete()
        manifestFile.write(XmlUtil.serialize(manifest), "utf-8")
    }

    private static int getMinSdkVersion(def mergedFlavor, String manifestPath) {
        if (mergedFlavor.minSdkVersion != null) {
            return mergedFlavor.minSdkVersion.apiLevel
        } else {
            return FreelineParser.getMinSdkVersion(manifestPath)
        }
    }

    private static void backUpClass(def backupMap, File file, String backUpDirPath, def modules) {
        String path = file.absolutePath
        if (!FreelineUtils.isEmpty(path)) {
            if (path.endsWith(".class")
                    || (path.endsWith(".jar") && FreelineInjector.checkInjection(file, modules as Collection))) {
                File target = new File(backUpDirPath, "${file.name}-${System.currentTimeMillis()}")
                FileUtils.copyFile(file, target)
                backupMap[file.absolutePath] = target.absolutePath
                println "back up ${file.absolutePath} to ${target.absolutePath}"
            }
        }
    }

    private static boolean isNeedBackUp(String path) {
        def pattern
        if (FreelineUtils.isWindows()) {
            pattern = ~".*\\\\R\\\$?\\w*.class"
        } else {
            pattern = ~".*/R\\\$?\\w*.class"
        }
        return pattern.matcher(path).matches()
    }

    private static void rollBackClasses(def backupMap) {
        backupMap.each { targetPath, sourcePath ->
            File sourceFile = new File(sourcePath as String)
            if (sourceFile.exists()) {
                try {
                    File targetFile = new File(targetPath as String)
                    FileUtils.deleteQuietly(targetFile)
                    FileUtils.moveFile(sourceFile, new File(targetPath as String))
                    println "roll back ${targetPath}"
                } catch (Exception e) {
                    println "roll back ${targetPath} failed: ${e.getMessage()}"
                }
            }
        }
    }

    private static boolean isProguardEnable(Project project, def variant) {
        def proguardTask = project.tasks.findByName("transformClassesAndResourcesWithProguardFor${variant.name.capitalize()}")
        if (proguardTask == null) {
            proguardTask = project.tasks.findByName("proguard${variant.name.capitalize()}")
        }
        return proguardTask != null
    }

    private static boolean isMultiDexEnabled(Project project, def variant) {
        if (variant.buildType.multiDexEnabled != null) {
            return variant.buildType.multiDexEnabled
        }
        if (variant.mergedFlavor.multiDexEnabled != null) {
            return variant.mergedFlavor.multiDexEnabled
        }
        return project.android.defaultConfig.multiDexEnabled
    }

    private static String isAptEnabled(Project project) {
        boolean isAptEnabled = false
        project.rootProject.buildscript.configurations.classpath.resolvedConfiguration.firstLevelModuleDependencies.each {
            if (it.moduleGroup == "com.neenbedankt.gradle.plugins" && it.moduleName == "android-apt") {
                isAptEnabled = true
                return false
            }
        }
        return isAptEnabled
    }

    private static VersionNumber retrolambdaVersion(Configuration retrolambdaConfig) {
        retrolambdaConfig.resolve()
        Dependency retrolambdaDep = retrolambdaConfig.dependencies.iterator().next()
        if (!retrolambdaDep.version) {
            // Don't know version
            return null
        }
        return VersionNumber.parse(retrolambdaDep.version)

    }

    private
    static boolean requireVersion(VersionNumber retrolambdaVersion, String version, boolean fallback = false) {
        if (retrolambdaVersion == null) {
            // Don't know version, assume fallback
            return fallback
        }
        def targetVersionNumber = VersionNumber.parse(version)
        return retrolambdaVersion >= targetVersionNumber
    }

    private static String getJdk8() {
        if ((System.properties.'java.version' as String).startsWith('1.8')) {
            return FreelineInitializer.getJavaHome()
        } else {
            return System.getenv("JAVA8_HOME")
        }
    }

    private static def getJavaCompileTask(def variant, Project pro) {
        def javaCompile
        if (pro.plugins.hasPlugin("com.android.application")) {
            javaCompile = variant.hasProperty('javaCompiler') ? variant.javaCompiler : variant.javaCompile
        } else {
            pro.android.libraryVariants.each { libraryVariant ->
                if ("release".equalsIgnoreCase(libraryVariant.buildType.name as String)) {
                    javaCompile = libraryVariant.hasProperty('javaCompiler') ? libraryVariant.javaCompiler : libraryVariant.javaCompile
                    return false
                }
            }
        }
        return javaCompile
    }

    private static def findAptConfig(Project project, def variant, def projectAptConfig) {
        def javaCompile = getJavaCompileTask(variant, project)

        def aptConfiguration = project.configurations.findByName("apt")
        def isAptEnabled = project.plugins.hasPlugin("android-apt") && aptConfiguration != null && !aptConfiguration.empty

        //只需要在AS3.0的plugin启用 在旧版启用会崩
        def shouldDealWithResolveProblem = false
        project.rootProject.buildscript.configurations.classpath.resolvedConfiguration.firstLevelModuleDependencies.each {
            if (it.moduleGroup == "com.android.tools.build" && it.moduleName == "gradle") {
                if (it.moduleVersion.startsWith("3")) {
                    shouldDealWithResolveProblem = true
                }
            }
        }
        if (shouldDealWithResolveProblem) {
            project.configurations.each {
                config -> config.setCanBeResolved(true)
            }
        }

        def annotationProcessorConfig = project.configurations.findByName("annotationProcessor")
//        annotationProcessorConfig.setCanBeResolved(true)
        def isAnnotationProcessor = annotationProcessorConfig != null && !annotationProcessorConfig.empty

        if ((isAptEnabled || isAnnotationProcessor) && javaCompile) {
            println "Freeline found ${project.name} apt plugin enabled."
            javaCompile.outputs.upToDateWhen { false }
            javaCompile.doFirst {
                def aptOutputDir
                if (project.plugins.hasPlugin("com.android.application")) {
                    aptOutputDir = new File(project.buildDir, "generated/source/apt/${variant.dirName}").absolutePath
                } else {
                    aptOutputDir = new File(project.buildDir, "generated/source/apt/release").absolutePath
                }

                def configurations = javaCompile.classpath
                if (isAptEnabled) {
                    configurations += aptConfiguration
                }
                if (isAnnotationProcessor) {
                    configurations += annotationProcessorConfig
                }

                def processorPath = configurations.asPath

                boolean disableDiscovery = javaCompile.options.compilerArgs.indexOf('-processorpath') == -1

                int processorIndex = javaCompile.options.compilerArgs.indexOf('-processor')
                def processor = null
                if (processorIndex != -1) {
                    processor = javaCompile.options.compilerArgs.get(processorIndex + 1)
                }

                def aptArgs = []
                javaCompile.options.compilerArgs.each { arg ->
                    if (arg.toString().startsWith('-A')) {
                        aptArgs.add(arg)
                    }
                }

                def aptConfig = ['enabled'         : true,
                                 'disableDiscovery': disableDiscovery,
                                 'aptOutput'       : aptOutputDir,
                                 'processorPath'   : processorPath,
                                 'processor'       : processor,
                                 'aptArgs'         : aptArgs]
                projectAptConfig[project.name] = aptConfig
            }
        } else {
            println "Freeline doesn't found apt plugin for $project.name"
        }
    }

}


