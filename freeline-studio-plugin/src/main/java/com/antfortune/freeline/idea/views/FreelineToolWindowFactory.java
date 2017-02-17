package com.antfortune.freeline.idea.views;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import org.jetbrains.annotations.NotNull;

/**
 * Created by pengwei on 16/9/16.
 */
public class FreelineToolWindowFactory implements ToolWindowFactory, DumbAware {

    public static final String TOOL_WINDOW_ID = "Freeline";

    public FreelineToolWindowFactory() {
    }

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        FreelineTerminal.getInstance(project).initTerminal(toolWindow);
    }
}
