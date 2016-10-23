package com.antfortune.freeline.databinding;

import java.io.File;

/**
 * Created by huangyong on 16/10/21.
 */
public class ExportDataBindingInfo {

    public static void run(File sdkDirectory, File outputDirectory) {
        // dataBindingExportBuildInfo
        // TODO: exportClassListTo
        DataBindingHelper.getLayoutXmlProcessor().writeInfoClass(sdkDirectory, outputDirectory, null, true, true);
    }

}
