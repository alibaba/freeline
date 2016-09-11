package com.antfortune.freeline.plugin.configuration;

import com.intellij.execution.configurations.ConfigurationTypeBase;
import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

/**
 * Freeline run configuration type
 *
 * @author act262@gmail.com
 */
public class FreeConfigurationType extends ConfigurationTypeBase {

    private final static String ID = "com.antfortune.freeline.run";
    private final static String DISPLAY_NAME = "Freeline Run";
    private final static String DESC = "Freeline Run Configuration";
    private final static Icon ICON = IconLoader.getIcon("/icons/ic_free.jpg");

    protected FreeConfigurationType() {
        super(ID, DISPLAY_NAME, DESC, ICON);
        this.addFactory(new FreeConfigurationFactory(this));
    }
}
