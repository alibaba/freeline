package com.antfortune.freeline.idea.configuration;

import com.intellij.execution.configurations.ConfigurationTypeBase;
import com.antfortune.freeline.idea.icons.PluginIcons;

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
    private final static Icon ICON = PluginIcons.ICON_ACTION_RUN;

    protected FreeConfigurationType() {
        super(ID, DISPLAY_NAME, DESC, ICON);
        this.addFactory(new FreeConfigurationFactory(this));
    }
}
