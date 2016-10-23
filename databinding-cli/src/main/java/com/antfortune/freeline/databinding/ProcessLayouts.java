package com.antfortune.freeline.databinding;

import android.databinding.tool.LayoutXmlProcessor;

import java.io.File;

/**
 * Created by huangyong on 16/10/21.
 */
public class ProcessLayouts {

    public static void run(File inputDirectory, File outputDirectory) throws Exception {
        LayoutXmlProcessor.ResourceInput resourceInput =
                new LayoutXmlProcessor.ResourceInput(false, inputDirectory, outputDirectory);

        // dataBindingProcessLayouts
        DataBindingHelper.getLayoutXmlProcessor().processResources(resourceInput);
        DataBindingHelper.getLayoutXmlProcessor().writeLayoutInfoFiles(outputDirectory);
    }

}
