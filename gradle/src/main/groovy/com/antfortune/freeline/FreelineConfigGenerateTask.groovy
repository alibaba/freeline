package com.antfortune.freeline

import org.apache.commons.io.FileUtils
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

/**
 * Created by huangyong on 16/9/13.
 */
class FreelineConfigGenerateTask extends DefaultTask {

    @Input
    public String applicationClass;

    @Input
    public String packageName;

    @OutputDirectory
    public File outputDir;

    @TaskAction
    public void action() {
        FileUtils.deleteDirectory(outputDir)

        Map<String, String> configMap = new HashMap<String, String>();
        configMap.put("packageName", packageName);
        configMap.put("applicationClass", applicationClass)
        FreelineGenerator.generateFreelineConfig(configMap, outputDir)
    }
}
