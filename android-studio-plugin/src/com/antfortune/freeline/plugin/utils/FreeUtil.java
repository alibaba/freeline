package com.antfortune.freeline.plugin.utils;

import com.antfortune.freeline.plugin.ui.FreeUIManager;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.impl.ContentImpl;

/**
 * Freeline Utility
 *
 * @author act262@gmail.com
 */
public class FreeUtil {

    public static void buildOrInit(Project project) {
        // TODO: 2016/9/12 0012 重复操作、超时处理

        // execute freeline script
        if (SystemUtil.hasInitFreeline(project)) {
            build(project);
        } else {
            initFree(project);
        }
    }

    public static void build(Project project) {
        GeneralCommandLine commandLine = new GeneralCommandLine();
        commandLine.setWorkDirectory(project.getBasePath());
        commandLine.setExePath("python");
        commandLine.addParameter("freeline.py");
        // debug
        commandLine.addParameter("-d");

        // commands process
        try {
            processCommandline(project, commandLine);
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }

    public static void initFree(Project project) {
        GeneralCommandLine commandLine = new GeneralCommandLine();
        commandLine.setWorkDirectory(project.getBasePath());

        // init freeline core file
        if (SystemUtil.isWindows()) {
            commandLine.setExePath("cmd");
            commandLine.addParameter("gradlew.bat");
        } else {
            commandLine.setExePath("/bin/sh");
            commandLine.addParameter("./gradlew");
        }
        commandLine.addParameters("initFreeline", "-Pmirror");

        // commands process
        try {
            processCommandline(project, commandLine);
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }

    /* process command line */
    private static void processCommandline(final Project project, GeneralCommandLine commandLine) throws ExecutionException {
        final OSProcessHandler processHandler = new OSProcessHandler(commandLine);
        ProcessTerminatedListener.attach(processHandler);
        processHandler.startNotify();

        ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
                processConsole(project, processHandler);
            }
        });
    }

    /* process attach to console,show the log */
    private static void processConsole(Project project, ProcessHandler processHandler) {
        ConsoleView consoleView = FreeUIManager.getInstance(project).getConsoleView(project);
        consoleView.clear();
        consoleView.attachToProcess(processHandler);

        ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
        ToolWindow toolWindow;
        toolWindow = toolWindowManager.getToolWindow(TOOL_ID);

        // if already exist tool window then show it
        if (toolWindow != null) {
            toolWindow.show(null);
            return;
        }

        toolWindow = toolWindowManager.registerToolWindow(TOOL_ID, true, ToolWindowAnchor.BOTTOM);
        toolWindow.setTitle("free....");
        toolWindow.setStripeTitle("Free Console");
        toolWindow.setShowStripeButton(true);
        toolWindow.setIcon(FreeIcons.ICON_TOOL_WINDOW);
        toolWindow.getContentManager().addContent(new ContentImpl(consoleView.getComponent(), "Build", true));
        toolWindow.show(null);
    }

    private final static String TOOL_ID = "Freeline Console";
}
