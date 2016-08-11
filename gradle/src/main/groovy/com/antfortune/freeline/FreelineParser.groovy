package com.antfortune.freeline
/**
 * Created by huangyong on 16/7/19.
 */
class FreelineParser {

    public static String getLauncher(String manifestPath, String packageName) {
        def launcher = ""
        def manifestFile = new File(manifestPath)
        if (manifestFile.exists() && manifestFile.isFile()) {
            def manifest = new XmlSlurper(false, false).parse(manifestFile)
            def launcherCandidates = []
            manifest.application.activity.each { node ->
                def candidate = [name: "", isDefault: false]
                if (node."intent-filter".category.size() > 0) {
                    node."intent-filter".category.each { category ->
                        if (category."@android:name" == "android.intent.category.LAUNCHER") {
                            candidate.name = node."@android:name".text()
                        } else if (category."@android:name" == "android.intent.category.DEFAULT") {
                            candidate.isDefault = true
                        }
                    }

                    if (candidate.name != "") {
                        launcherCandidates.add(candidate)
                    }
                }
            }

            if (launcherCandidates.size() == 0) {
                // throw exception
            } else if (launcherCandidates.size() == 1) {
                launcher = launcherCandidates[0].name
            } else {
                launcherCandidates.each {
                    if (it.isDefault) {
                        launcher = it.name
                        return true
                    }
                }
            }

            if (launcher.startsWith(".")) {
                launcher = packageName + launcher
            }
        }
        return launcher
    }

}
