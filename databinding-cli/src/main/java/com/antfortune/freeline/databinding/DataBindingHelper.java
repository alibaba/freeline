package com.antfortune.freeline.databinding;

import android.databinding.tool.DataBindingBuilder;
import android.databinding.tool.LayoutXmlProcessor;

import java.io.File;

/**
 * Created by huangyong on 16/10/21.
 */
public class DataBindingHelper {

    private static LayoutXmlProcessor sLayoutXmlProcessor;

    public static void init(String packageName, int minSdkVersion, String classOutputPath, boolean isLibrary) {
        if (sLayoutXmlProcessor == null) {
            sLayoutXmlProcessor = new LayoutXmlProcessor(
                    packageName,
                    new DataBindingBuilder().createJavaFileWriter(new File(classOutputPath)),
                    minSdkVersion,
                    isLibrary,
                    new LayoutXmlProcessor.OriginalFileLookup() {
                        @Override
                        public File getOriginalFileFor(File file) {
                            return file;
                        }
                    }
            );
        }
    }

    public static LayoutXmlProcessor getLayoutXmlProcessor() {
        return sLayoutXmlProcessor;
    }

}
