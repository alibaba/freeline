package com.antfortune.freeline

/**
 * Created by huangyong on 17/1/12.
 */
class FreelineCompat {

    private static final def IGNORE_RESOURCE_IDS = ["avd_hide_password_1", "avd_hide_password_2",
                                                    "avd_hide_password_3", "avd_show_password_1",
                                                    "avd_show_password_2", "avd_show_password_3"]

    public static List<String> compatIgnoreResourceIds(List<String> ignoreResourceIds) {
        for (String id : IGNORE_RESOURCE_IDS) {
            if (!ignoreResourceIds.contains(id)) {
                ignoreResourceIds.add(id)
            }
        }
        return ignoreResourceIds
    }

}
