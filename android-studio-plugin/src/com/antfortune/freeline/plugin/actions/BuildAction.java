package com.antfortune.freeline.plugin.actions;

import com.antfortune.freeline.plugin.utils.FreeUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;

/**
 * build or init freeline action
 *
 * @author act262@gmail.com
 */
public class BuildAction extends AnAction {

    private static final Logger LOG = Logger.getInstance(BuildAction.class);

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project currentProject = DataKeys.PROJECT.getData(e.getDataContext());
        FileDocumentManager.getInstance().saveAllDocuments();

        performBuild(currentProject);
    }

    private void performBuild(Project project) {
        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
            @Override
            public void run() {
                // build or init project
                FreeUtil.buildOrInit(project);
            }
        });
    }

    @Override
    public void update(AnActionEvent e) {
        // update button state
    }
}