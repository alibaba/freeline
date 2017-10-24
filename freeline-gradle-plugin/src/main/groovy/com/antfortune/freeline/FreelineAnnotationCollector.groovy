package com.antfortune.freeline

import groovy.json.JsonBuilder
import org.gradle.api.Project;

/**
 * Created by huangyong on 16/11/3.
 */
class FreelineAnnotationCollector {

    public static final def ANNOTATION_CLASSES = [
        "Landroid/databinding/BindingAdapter;",
        "Landroid/databinding/BindingConversion;",
        "Landroid/databinding/Bindable;",
        "Ldagger/Component;",
        "Ldagger/Module;",
        "Ljavax/inject/Inject;"
    ]

    public static final def ANNOTATION_TARGETS = [
        "Landroid/databinding/BindingAdapter;": "BindingAdapter",
        "Landroid/databinding/BindingConversion;": "BindingConversion",
        "Landroid/databinding/Bindable;": "Bindable",
        "Ldagger/Component;": "DaggerComponent",
        "Ldagger/Module;": "DaggerModule",
        "Ljavax/inject/Inject;": "Inject"
    ]

    public static final def CUSTOM_ANNOTATION_TARGETS = [:]

    private static def sAnnotationCollection = [:]

    // 收集所有注解信息 以供后续观察 DSL自定义注解规则可以参考
    public static final def DEBUG_ANNOTATION_COLLECTOR = new HashSet<String>()

    public static void addNewAnno(String anno, String path, String className, String entry, boolean isJar) {
        String key = ANNOTATION_TARGETS[anno]

        println "custom anno settings enabled :)==> $CUSTOM_ANNOTATION_TARGETS"

        if (key == null){
            CUSTOM_ANNOTATION_TARGETS.keySet().each { annoToken ->
                if (anno.contains(annoToken)){
                    key = CUSTOM_ANNOTATION_TARGETS[annoToken]
                }
            }
        }

        if (!sAnnotationCollection.containsKey(key)) {
            sAnnotationCollection[key] = []
            //print 出增加适配的key
            println "new anno --> ${(['path': path, 'className': className, 'entry': entry, 'isJar': isJar]).toString()}"
        }

        sAnnotationCollection[key].add(['path': path, 'className': className, 'entry': entry, 'isJar': isJar])
    }

    public static void saveCollections(Project project, String buildCacheDirPath, Map modules) {
        def description = FreelineUtils.readProjectDescription(project)
        sAnnotationCollection.keySet().each { key ->
            sAnnotationCollection[key].each { value ->
                if (value['isJar']) {
                    modules.each { m, mappers ->
                        for (String mapper : mappers) {
                            if (value['path'].contains(mapper)) {
                                value['module'] = m
                                value['java_path'] = findJavaPath(description, m as String, value['className'] as String)
                                return false
                            }
                        }
                    }
                } else {
                    value['module'] = project.name
                    value['java_path'] = findJavaPath(description, project.name, value['className'] as String)
                }
            }
        }

        def json = new JsonBuilder(sAnnotationCollection).toPrettyString()
        println json
        FreelineUtils.saveJson(json, FreelineUtils.joinPath(buildCacheDirPath, "freeline_annotation_info.json"), true)

        def allAnnotationJson = new JsonBuilder(DEBUG_ANNOTATION_COLLECTOR).toPrettyString()
        println allAnnotationJson
        FreelineUtils.saveJson(allAnnotationJson, FreelineUtils.joinPath(buildCacheDirPath, "freeline_debug_annotation_collection.json"), true)

        sAnnotationCollection.clear()
    }

    private static String findJavaPath(def description, String module, String className) {
        if (description != null) {
            if (description['project_source_sets'].containsKey(module)) {
                def relatedPath = className.replace("/", File.separator).replace(".class", ".java")
                if (!relatedPath.endsWith(".java")) {
                    relatedPath = relatedPath + ".java"
                }

                def javaPath = null
                description['project_source_sets'][module]['main_src_directory'].each { path ->
                    File file = new File(FreelineUtils.joinPath(path as String, relatedPath))
                    if (file.exists()) {
                        javaPath = file.absolutePath
                        return false
                    }
                }

                if (javaPath != null) {
                    return javaPath
                }
            }
        }
        return null
    }

}
