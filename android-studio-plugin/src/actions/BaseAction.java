package actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.jediterm.terminal.model.TerminalTextBuffer;
import org.jetbrains.plugins.terminal.JBTabbedTerminalWidget;
import org.jetbrains.plugins.terminal.JBTerminalWidget;
import utils.NotificationUtils;

import javax.swing.*;
import java.io.File;

/**
 * Created by pengwei on 16/9/11.
 */
public abstract class BaseAction extends AnAction {

    public static final String BREAK_LINE = System.getProperty("line.separator");

    protected Project currentProject;
    protected File projectDir;
    protected AnActionEvent anActionEvent;

    @Override
    public final void actionPerformed(AnActionEvent anActionEvent) {
        this.anActionEvent = anActionEvent;
        this.currentProject = DataKeys.PROJECT.getData(anActionEvent.getDataContext());
        this.projectDir = new File(currentProject.getBasePath());
        actionPerformed();
    }

    public abstract void actionPerformed();

    /**
     * 检查FreeLine是否存在
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
     * 在Terminal中输入shell
     *
     * @param shell
     */
    protected void executeShell(String shell) {
        ToolWindow window = ToolWindowManager.getInstance(currentProject).getToolWindow("Terminal");
        if (window == null || window.getContentManager().getContentCount() < 1) {
            NotificationUtils.errorNotification("can't find plugin Terminal");
            return;
        }
        window.activate(null);
        if (window.getContentManager().getContents()[0].getComponent() instanceof SimpleToolWindowPanel) {
            SimpleToolWindowPanel panel = (SimpleToolWindowPanel) window.getContentManager().getContents()[0].getComponent();
            if (panel == null || panel.getComponentCount() < 1) {
                NotificationUtils.errorNotification("can't find SimpleToolWindowPanel");
            } else {
                JPanel jPanel = (JPanel) panel.getComponents()[0];
                if (jPanel == null || jPanel.getComponentCount() < 1) {
                    NotificationUtils.errorNotification("JPanel's Components is null");
                } else {
                    JBTabbedTerminalWidget widget = (JBTabbedTerminalWidget) jPanel.getComponents()[0];
                    if (widget == null || widget.getComponentCount() < 1) {
                        NotificationUtils.errorNotification("can't find JBTabbedTerminalWidget");
                    } else {
                        JBTerminalWidget terminalWidget = (JBTerminalWidget) widget.getComponents()[0];
                        TerminalTextBuffer buffer = terminalWidget.getTerminalTextBuffer();
                        String lastLineText = buffer.getLine(buffer.getScreenLinesCount() - 1).getText().trim();
                        // TODO: 16/9/11 没找到清空terminal好的方案，暂时用以下方案代替
                        // 判断terminal是否已经输入其他内容
                        shell = shell + " " + BREAK_LINE;
                        if (!lastLineText.endsWith("$")) {
                            shell = "#" + BREAK_LINE + shell;
                        }
                        terminalWidget.getTerminalStarter().sendString(shell);
                    }
                }
            }
        } else {
            NotificationUtils.errorNotification("Wait for Terminal to initialize");
        }
    }

    protected void executeShell(String[] shell) {
        StringBuilder build = new StringBuilder();
        if (shell != null && shell.length > 0) {
            for (String s : shell) {
                if (s == null) {
                    continue;
                }
                build.append(s + " ");
            }
        }
        executeShell(build.toString());
    }
}
