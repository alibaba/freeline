package com.antfortune.freeline.plugin.actions;

import com.antfortune.freeline.plugin.utils.SystemUtil;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.impl.ContentImpl;

/**
 * build or init freeline action
 *
 * @author act262@gmail.com
 */
public class BuildAction extends AnAction {

    private final static String TOOL_ID = "Freeline Console";

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project currentProject = DataKeys.PROJECT.getData(e.getDataContext());
        FileDocumentManager.getInstance().saveAllDocuments();

        GeneralCommandLine commandLine = new GeneralCommandLine();
        commandLine.setWorkDirectory(currentProject.getBasePath());

        // execute freeline script
        if (SystemUtil.hasInitFreeline(currentProject)) {
            commandLine.setExePath("python");
            commandLine.addParameters("freeline.py");
        } else {
            // init freeline core file
            if (SystemUtil.isWindows()) {
                commandLine.setExePath("cmd");
                commandLine.addParameter("gradlew.bat");
            } else {
                commandLine.setExePath("/bin/sh");
                commandLine.addParameter("./gradlew");
            }
            commandLine.addParameters("initFreeline", "-Pmirror");
        }

        // TODO: 2016/9/12 0012 重复操作、超时处理

        // commands process
        processCommandline(currentProject, commandLine);
    }

    /* process command line */
    private void processCommandline(Project project, GeneralCommandLine commandLine) {
        try {
            OSProcessHandler processHandler = new OSProcessHandler(commandLine);
            ProcessTerminatedListener.attach(processHandler);
            processHandler.startNotify();
//            processHandler.waitFor();

            processConsole(project, processHandler);
        } catch (ExecutionException e1) {
            e1.printStackTrace();
        }
    }

    /* process attach to console,show the log */
    private void processConsole(Project project, ProcessHandler processHandler) {
        // TODO: 2016/9/12 0012 窗口调整
        ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
        ToolWindow toolWindow;
        toolWindow = toolWindowManager.getToolWindow(TOOL_ID);
        if (toolWindow != null) {
            toolWindow.show(null);
            return;
        }

        ConsoleView consoleView = TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();
        consoleView.attachToProcess(processHandler);
        toolWindow = toolWindowManager.registerToolWindow(TOOL_ID, true, ToolWindowAnchor.BOTTOM);
        toolWindow.setTitle("free....");
        toolWindow.setShowStripeButton(true);
        toolWindow.setIcon(IconLoader.getIcon("/icons/ic_free.jpg"));
        toolWindow.getContentManager().addContent(new ContentImpl(consoleView.getComponent(), "", true));
        toolWindow.show(null);
    }

    private void showTip() {
    }
}