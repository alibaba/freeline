package com.antfortune.freeline.databinding;

import android.databinding.tool.LayoutXmlProcessor;

import java.io.File;
import java.util.List;

/**
 * Created by huangyong on 16/10/21.
 */
public class ProcessLayouts {

    public static void run(boolean isIncremental, File inputDirectory, File outputDirectory, File layoutInfoDirectory, List<String> changedFiles) throws Exception {
        LayoutXmlProcessor.ResourceInput resourceInput =
                new LayoutXmlProcessor.ResourceInput(isIncremental, inputDirectory, outputDirectory);
        if (isIncremental && changedFiles != null) {
            for (String path : changedFiles) {
                resourceInput.changed(new File(path));
            }
        }

        // dataBindingProcessLayouts
        DataBindingHelper.getLayoutXmlProcessor().processResources(resourceInput);
        DataBindingHelper.getLayoutXmlProcessor().writeLayoutInfoFiles(layoutInfoDirectory);
    }

}
