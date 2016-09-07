package com.antfortune.freeline.plugin.actions;

import com.antfortune.freeline.plugin.SettingComponent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;

/**
 * Created by zcx on 16-9-6.
 */
public class SettingAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        Application application = ApplicationManager.getApplication();
        SettingComponent component = application.getComponent(SettingComponent.class);
        component.show();
    }
}
