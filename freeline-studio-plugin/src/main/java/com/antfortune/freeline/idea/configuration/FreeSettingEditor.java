package com.antfortune.freeline.idea.configuration;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Run configuration setting editor
 * <p>
 * TODO: not implementation ui
 *
 * @author act262@gmail.com
 */
public class FreeSettingEditor extends SettingsEditor<FreeRunConfiguration> {
    private JPanel panel;

    @Override
    protected void resetEditorFrom(@NotNull FreeRunConfiguration freeRunConfiguration) {
    }

    @Override
    protected void applyEditorTo(@NotNull FreeRunConfiguration freeRunConfiguration) throws ConfigurationException {
    }

    @NotNull
    @Override
    protected JComponent createEditor() {
        return panel;
    }

}
