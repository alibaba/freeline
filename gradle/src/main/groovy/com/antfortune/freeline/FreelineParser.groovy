package com.antfortune.freeline
/**
 * Created by huangyong on 16/7/19.
 */
class FreelineParser {

    public static String getPackageName(String applicationId, String manifestPath) {
        if (!FreelineUtils.isEmpty(applicationId) && !"null".equals(applicationId)) {
            return applicationId
        }
        return getPackage(manifestPath)
    }

    public static String getPackage(String manifestPath) {
        def packageName = ""
        def manifestFile = new File(manifestPath)
        if (manifestFile.exists() && manifestFile.isFile()) {
            def manifest = new XmlSlurper(false, false).parse(manifestFile)
            packageName = manifest."@package".text()
        }
        return packageName
    }

    public static String getApplication(String manifestPath, String packageName) {
        def application = ""
        packageName = fetchRealPackageName(manifestPath, packageName)
        def manifestFile = new File(manifestPath)
        if (manifestFile.exists() && manifestFile.isFile()) {
            def manifest = new XmlSlurper(false, false).parse(manifestFile)
            application = manifest.application."@android:name".text()
            if (application != null && application.startsWith(".")) {
                application = packageName + application
            }
        }
        return application
    }

    public static String getLauncher(String manifestPath, String packageName) {
        def launcher = ""
        packageName = fetchRealPackageName(manifestPath, packageName)
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

    public static int getMinSdkVersion(String manifestPath) {
        def minSdkVersion = 0
        def manifestFile = new File(manifestPath)
        if (manifestFile.exists() && manifestFile.isFile()) {
            def manifest = new XmlSlurper(false, false).parse(manifestFile)
            minSdkVersion = manifest."uses-sdk"."@android:minSdkVersion".text()
        }
        return Integer.valueOf(minSdkVersion)
    }

    private static String fetchRealPackageName(String manifestPath, String packageName) {
        def manifestPackageName = getPackage(manifestPath)
        if (manifestPackageName != null && !manifestPackageName.equals(packageName)) {
            packageName = manifestPackageName
        }
        return packageName
    }

}
