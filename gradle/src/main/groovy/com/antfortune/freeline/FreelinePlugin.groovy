package com.antfortune.freeline

import groovy.io.FileType
import groovy.json.JsonBuilder
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Created by yeqi on 16/5/3.
 */
class FreelinePlugin implements Plugin<Project> {

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
                if (!variant.name.endsWith("debug") && !variant.name.endsWith("Debug")) {
                    println "variant ${variant.name} is not debug, skip hack process."
                    return
                }

                println "find variant ${variant.name} start hack process..."

                def extension = project.extensions.findByName("freeline") as FreelineExtension
                def hack = extension.hack
                def productFlavor = extension.productFlavor
                def excludeHackClasses = extension.excludeHackClasses
                def forceLowerVersion = extension.foceLowerVersion

                if (!hack) {
                    return
                }

                // add addtional aapt args
                def publicKeeperGenPath = FreelineUtils.joinPath(FreelineUtils.getBuildCacheDir(project.buildDir.absolutePath), "public_keeper.xml")
                project.android.aaptOptions.additionalParameters("-P", publicKeeperGenPath)

                // force tasks to run
                project.tasks.findByName("merge${variant.name.capitalize()}Assets").outputs.upToDateWhen { false }

                // add freeline generated files to assets
                project.tasks.findByName("merge${variant.name.capitalize()}Assets").doLast {
                    addFreelineGeneratedFiles(project, new File(FreelineGenerator.generateProjectBuildAssetsPath(project.buildDir.absolutePath, productFlavor)), null)
                }

                // find thrid party libraries' resources dependencies
                project.rootProject.allprojects.each { p ->
                    findResourceDependencies(variant, p, FreelineUtils.getBuildCacheDir(project.buildDir.absolutePath))
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
                    if (project.android.defaultConfig.multiDexEnabled) {
                        classesProcessTask = project.tasks.findByName("packageAll${variant.name.capitalize()}ClassesForMultiDex")
                    } else {
                        classesProcessTask = project.tasks.findByName("dex${variant.name.capitalize()}")
                        preDexTask = project.tasks.findByName("preDex${variant.name.capitalize()}")
                    }
                } else {
                    if (project.android.defaultConfig.multiDexEnabled) {
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
                    def mapper = ["match" : "", "path" : p.android.sourceSets.main.res.srcDirs.asList().get(0).path]
                    mapper.match = "exploded-aar${File.separator}${p.group}${File.separator}${p.name}${File.separator}"
                    mappers.add(mapper)
                }
            }

            mergeResourcesTask.inputs.files.files.each { f ->
                def path = f.absolutePath
                if (path.contains("exploded-aar")) {
                    def marker = false
                    mappers.each { mapper ->
                        if (path.contains(mapper.match)) {
                            resourcesDependencies.local_resources.add(mapper.path)
                            marker = true
                            return false
                        }
                    }
                    if (!marker) {
                        resourcesDependencies.library_resources.add(path)
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

}


