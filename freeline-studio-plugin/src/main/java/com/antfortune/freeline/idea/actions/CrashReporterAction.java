package com.antfortune.freeline.idea.actions;

import com.antfortune.freeline.idea.models.FreelineConfiguration;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;

/**
 * Created by huangyong on 17/2/14.
 */
public class CrashReporterAction extends ToggleAction {

    private FreelineConfiguration mConfigurationStorage;

    public CrashReporterAction() {
        mConfigurationStorage = FreelineConfiguration.getInstance();
    }

    @Override
    public boolean isSelected(AnActionEvent anActionEvent) {
        return mConfigurationStorage.DISABLE_CRASH_REPORTER;
    }

    @Override
    public void setSelected(AnActionEvent anActionEvent, boolean b) {
        mConfigurationStorage.DISABLE_CRASH_REPORTER = b;
    }
}
