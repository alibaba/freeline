package com.antfortune.freeline.idea.utils;

import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;

/**
 * UI manager service
 */
public class FreeUIManager {

    public static FreeUIManager getInstance(Project project) {
        return ServiceManager.getService(project, FreeUIManager.class);
    }

    private ConsoleView mFreeConsole;

    public ConsoleView getConsoleView(Project project) {
        if (mFreeConsole == null) {
            mFreeConsole = TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();
        }
        return mFreeConsole;
    }
}
