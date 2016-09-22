package actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import utils.NotificationUtils;

import java.io.File;

/**
 * Created by pengwei on 16/9/11.
 */
public abstract class BaseAction extends AnAction {

    protected Project currentProject;
    protected File projectDir;
    protected AnActionEvent anActionEvent;

    @Override
    public final void actionPerformed(AnActionEvent anActionEvent) {
        saveDocument();
        this.anActionEvent = anActionEvent;
        this.currentProject = DataKeys.PROJECT.getData(anActionEvent.getDataContext());
        this.projectDir = new File(currentProject.getBasePath());
        actionPerformed();
    }

    private void saveDocument() {
        FileDocumentManager.getInstance().saveAllDocuments();
        ApplicationManager.getApplication().saveSettings();
    }

    public abstract void actionPerformed();

    /**
     * 检查FreeLine是否存在
     *
     * @return
     */
    protected boolean checkFreeLineExist() {
        File pyFile = new File(projectDir, "freeline.py");
        if (pyFile.exists()) {
            return true;
        }
        NotificationUtils.errorNotification("please install FreeLine first");
        return false;
    }

    /**
     * 异步执行
     *
     * @param runnable
     */
    protected void asyncTask(Runnable runnable) {
        ApplicationManager.getApplication().executeOnPooledThread(runnable);
    }

    protected void invokeLater(Runnable runnable) {
        ApplicationManager.getApplication().invokeLater(runnable);
    }


}
