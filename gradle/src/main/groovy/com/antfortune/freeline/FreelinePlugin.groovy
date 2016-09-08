package com.antfortune.freeline

import groovy.io.FileType
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Created by yeqi on 16/5/3.
 */
class FreelinePlugin implements Plugin<Project> {

    public static def getProperty(Project project, String property) {
        if (project.hasProperty(property)) {
            return project.getProperties()[property];
        }
        return null;
    }

    @Override
    void apply(Project project) {

        project.extensions.create("freeline", FreelineExtension, project)

        project.rootProject.task("initFreeline") << {
            FreelineInitializer.initFreeline(project)
        }

        project.rootProject.task("checkBeforeCleanBuild") << {
            FreelineInitializer.generateProjectDescription(project)
        }

        project.afterEvaluate {
            // check
            if (!project.plugins.hasPlugin("com.android.application")) {
                throw new RuntimeException("Freeline plugin can only be applied for android application module.")
            }

            if (!project.hasProperty("freeline")) {
                throw new RuntimeException("You should add freeline DSL to your main module's build.gradle before execute gradle command.")
            }
            
            project.android.applicationVariants.each { variant ->
                def extension = project.extensions.findByName("freeline") as FreelineExtension
                def hack = extension.hack
                def productFlavor = extension.productFlavor
                def apkPath = extension.apkPath
                def excludeHackClasses = extension.excludeHackClasses
                def forceLowerVersion = extension.foceLowerVersion
                def freelineBuild = getProperty(project, "freelineBuild");


                if (!"debug".equalsIgnoreCase(variant.buildType.name)) {
                    println "variant ${variant.name} is not debug, skip hack process."
                    return
                } else if (!FreelineUtils.isEmpty(productFlavor) && !productFlavor.toString().equalsIgnoreCase(variant.flavorName)) {
                    println "variant ${variant.name} is not ${productFlavor}, skip hack process."
                    return
                }

                println "find variant ${variant.name} start hack process..."

                if (!hack || !freelineBuild) {
                    return
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
                    def descriptionFile = new File(FreelineUtils.joinPath(FreelineUtils.getFreelineCacheDir(project.rootDir.absolutePath), 'project_description.json'))
                    if (descriptionFile.exists()) {
                        def description = new JsonSlurper().parseText(descriptionFile.text)
                        description.apk_path = FreelineUtils.getDefaultApkPath(apk_paths, project.buildDir.absolutePath, project.name, description.product_flavor)
                        println "find default apk path: ${description.apk_path}"
                        FreelineUtils.saveJson(new JsonBuilder(description).toPrettyString(), descriptionFile.absolutePath, true)
                    }
                }

                // force tasks to run
                def mergeAssetsTask = project.tasks.findByName("merge${variant.name.capitalize()}Assets")
                mergeAssetsTask.outputs.upToDateWhen { false }

                // add freeline generated files to assets
                mergeAssetsTask.doLast {
                    addFreelineGeneratedFiles(project, new File(FreelineGenerator.generateProjectBuildAssetsPath(project.buildDir.absolutePath, productFlavor)), null)

                    // add addtional aapt args
                    def publicKeeperGenPath = FreelineUtils.joinPath(FreelineUtils.getBuildCacheDir(project.buildDir.absolutePath), "public_keeper.xml")
                    project.android.aaptOptions.additionalParameters("-P", publicKeeperGenPath)
                    println "Freeline add additionalParameters `-P ${publicKeeperGenPath}` to aaptOptions"
                }

                // find thrid party libraries' resources dependencies
                project.rootProject.allprojects.each { p ->
                    findResourceDependencies(variant, p, FreelineUtils.getBuildCacheDir(project.buildDir.absolutePath))
                }

                // find additional jars
                def addtionalJars = []
                if (project.android.hasProperty("libraryRequests")) {
                    project.android.libraryRequests.each { p ->
                        def jar_path = FreelineUtils.joinPath(
                                project.android.sdkDirectory.toString(),
                                'platforms',
                                project.android.compileSdkVersion.toString(),
                                'optional',
                                p.name + ".jar")
                        def f = new File(jar_path)
                        if (f.exists() && f.isFile()) {
                            addtionalJars.add(jar_path)
                            println "find additional jar: ${jar_path}"
                        }
                    }
                }

                // modify .class file
                def isLowerVersion = false
                if (!forceLowerVersion) {
                    project.rootProject.buildscript.configurations.classpath.resolvedConfiguration.firstLevelModuleDependencies.each {
                        if (it.moduleGroup == "com.android.tools.build" && it.moduleName == "gradle") {
                            if (it.moduleVersion.startsWith("1")) {
                                isLowerVersion = true
                                return false
                            }
                        }
                    }
                } else {
                    isLowerVersion = true
                }

                def classesProcessTask
                def preDexTask
                if (isLowerVersion) {
                    if (variant.mergedFlavor.multiDexEnabled) {
                        classesProcessTask = project.tasks.findByName("packageAll${variant.name.capitalize()}ClassesForMultiDex")
                    } else {
                        classesProcessTask = project.tasks.findByName("dex${variant.name.capitalize()}")
                        preDexTask = project.tasks.findByName("preDex${variant.name.capitalize()}")
                    }
                } else {
                    String manifest_path = project.android.sourceSets.main.manifest.srcFile.path
                    if (getMinSdkVersion(variant.mergedFlavor, manifest_path) < 21 && variant.mergedFlavor.multiDexEnabled) {
                        classesProcessTask = project.tasks.findByName("transformClassesWithJarMergingFor${variant.name.capitalize()}")
                    } else {
                        classesProcessTask = project.tasks.findByName("transformClassesWithDexFor${variant.name.capitalize()}")
                    }
                }

                if (classesProcessTask == null) {
                    println "Skip ${project.name}'s hack process"
                    return
                }

                classesProcessTask.outputs.upToDateWhen { false }

                if (preDexTask) {
                    preDexTask.outputs.upToDateWhen { false }
                    def hackClassesBeforePreDex = "hackClassesBeforePreDex${variant.name.capitalize()}"
                    project.task(hackClassesBeforePreDex) << {
                        def jarDependencies = []
                        def modules = []
                        project.rootProject.allprojects.each { pro ->
                            modules.add("exploded-aar" + File.separator + pro.group + File.separator + pro.name + File.separator)
                        }

                        preDexTask.inputs.files.files.each { f ->
                            if (f.path.endsWith(".jar")) {
                                FreelineInjector.inject(excludeHackClasses, f, modules)
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
                project.task(hackClassesBeforeDex) << {
                    def jarDependencies = []
                    def modules = []
                    project.rootProject.allprojects.each { pro ->
                        modules.add("exploded-aar" + File.separator + pro.group + File.separator + pro.name + File.separator)
                    }

                    classesProcessTask.inputs.files.files.each { f ->
                        if (f.isDirectory()) {
                            f.eachFileRecurse(FileType.FILES) { file ->
                                FreelineInjector.inject(excludeHackClasses, file, modules)
                            }
                            if (f.path.endsWith(".jar")) {
                                jarDependencies.add(f.path)
                            }
                        } else {
                            FreelineInjector.inject(excludeHackClasses, f, modules)
                            if (f.path.endsWith(".jar")) {
                                jarDependencies.add(f.path)
                            }
                        }
                    }

                    if (preDexTask == null) {
                        jarDependencies.addAll(addtionalJars)  // add all additional jars to final jar dependencies
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
            }
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

    private static void findResourceDependencies(def variant, Project project, String buildCacheDir) {
        def mergeResourcesTask = project.tasks.findByName("merge${variant.name.capitalize()}Resources")
        def resourcesInterceptor = "resourcesInterceptorBeforeMerge${variant.name.capitalize()}Resources"
        if (mergeResourcesTask == null) {
            mergeResourcesTask = project.tasks.findByName("mergeReleaseResources")
        }
        if (mergeResourcesTask == null) {
            mergeResourcesTask = project.tasks.findByName("mergeDebugResources")
        }

        if (mergeResourcesTask == null) {
            println "${project.name} merge resources task not found."
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
                    def mapper = ["match" : "", "path" : []]
                    mapper.match = "exploded-aar${File.separator}${p.group}${File.separator}${p.name}${File.separator}"
                    p.android.sourceSets.main.res.srcDirs.asList().collect(mapper.path) { it.absolutePath }
                    mappers.add(mapper)
                }
            }

            def projectResDirs = []
            project.android.sourceSets.main.res.srcDirs.asList().collect(projectResDirs) { it.absolutePath }

            mergeResourcesTask.inputs.files.files.each { f ->
                if (f.exists() && f.isDirectory()) {
                    def path = f.absolutePath
                    if (path.contains("exploded-aar")) {
                        def marker = false
                        mappers.each { mapper ->
                            if (path.contains(mapper.match)) {
                                mapper.path.collect(resourcesDependencies.local_resources) {it}
                                marker = true
                                return false
                            }
                        }
                        if (!marker) {
                            resourcesDependencies.library_resources.add(path)
                        }
                    } else {
                        if (!projectResDirs.contains(path)) {
                            resourcesDependencies.library_resources.add(path)
                        }
                    }
                }
            }

            def json = new JsonBuilder(resourcesDependencies).toPrettyString()
            project.logger.info(json)
            FreelineUtils.saveJson(json, FreelineUtils.joinPath(dir.absolutePath, "resources_dependencies.json"), true);
        }

        def resourcesInterceptorTask = project.tasks[resourcesInterceptor]
        resourcesInterceptorTask.dependsOn mergeResourcesTask.taskDependencies.getDependencies(mergeResourcesTask)
        mergeResourcesTask.dependsOn resourcesInterceptorTask
    }

    private static int getMinSdkVersion(def mergedFlavor, String manifestPath) {
        if (mergedFlavor.minSdkVersion != null) {
            return mergedFlavor.minSdkVersion.apiLevel
        } else {
            return FreelineParser.getMinSdkVersion(manifestPath)
        }
    }

}


