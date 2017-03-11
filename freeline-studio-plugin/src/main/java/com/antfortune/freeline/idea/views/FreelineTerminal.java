package com.antfortune.freeline.idea.views;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.jediterm.terminal.Terminal;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.terminal.ui.JediTermWidget;
import com.antfortune.freeline.idea.icons.PluginIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.terminal.AbstractTerminalRunner;
import org.jetbrains.plugins.terminal.JBTabbedTerminalWidget;
import org.jetbrains.plugins.terminal.LocalTerminalDirectRunner;
import com.antfortune.freeline.idea.utils.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.List;

/**
 * Created by pengwei on 16/9/15.
 */
public class FreelineTerminal implements FocusListener, ProjectComponent {

    private JBTabbedTerminalWidget myTerminalWidget;
    private Project myProject;

    public FreelineTerminal(Project project) {
        this.myProject = project;
    }

    public static FreelineTerminal getInstance(Project project) {
        return project.getComponent(FreelineTerminal.class);
    }

    public JBTabbedTerminalWidget getTerminalWidget(ToolWindow window) {
        window.show(null);
        if (myTerminalWidget == null) {
            JComponent parentPanel =  window.getContentManager().getContents()[0].getComponent();
            if (parentPanel instanceof SimpleToolWindowPanel) {
                SimpleToolWindowPanel panel = (SimpleToolWindowPanel) parentPanel;
                JPanel jPanel = (JPanel) panel.getComponents()[0];
                myTerminalWidget = (JBTabbedTerminalWidget) jPanel.getComponents()[0];
            } else {
                NotificationUtils.infoNotification("Wait for Freeline to initialize");
            }
        }
        return myTerminalWidget;
    }

    public JBTabbedTerminalWidget getTerminalWidget() {
        ToolWindow window = getToolWindow();
        return getTerminalWidget(window);
    }

    public JediTermWidget getCurrentSession() {
        if (getTerminalWidget() != null) {
            return getTerminalWidget().getCurrentSession();
        }
        return null;
    }

    /**
     * 在terminal输入shell
     */
    private void sendString(String shell) {
        if (getCurrentSession() != null) {
            getCurrentSession().getTerminalStarter().sendString(shell);
        }
    }

    public void initAndExecute(final String[] shell) {
        ToolWindow toolWindow = getToolWindow();
        if (toolWindow.isActive()) {
            executeShell(shell);
        } else {
            toolWindow.activate(new Runnable() {
                @Override
                public void run() {
                    executeShell(shell);
                }
            });
        }
    }

    /**
     * 执行shell
     * 利用terminal换行即执行原理
     *
     * @param shell
     */
    public void executeShell(String shell) {
        if (getCurrentSession() != null) {
            TerminalTextBuffer buffer = getTerminalWidget().getCurrentSession().getTerminalTextBuffer();
            String lastLineText = buffer.getLine(buffer.getScreenLinesCount() - 1).getText().trim();
            shell = shell + " " + Utils.BREAK_LINE;
            if (!lastLineText.endsWith("$") && lastLineText.trim().length() != 0) {
                shell = "#" + Utils.BREAK_LINE + shell;
            }
            sendString(shell);
        }
    }

    /**
     * 执行shell
     *
     * @param shell
     */
    public void executeShell(String[] shell) {
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

    public void initTerminal(final ToolWindow toolWindow) {
        toolWindow.setToHideOnEmptyContent(true);
        LocalTerminalDirectRunner terminalRunner = LocalTerminalDirectRunner.createTerminalRunner(myProject);
        toolWindow.setStripeTitle("Freeline");
        Content content = createTerminalInContentPanel(terminalRunner, toolWindow);
        toolWindow.getContentManager().addContent(content);
        toolWindow.setShowStripeButton(true);
        toolWindow.setTitle("Console");
        ((ToolWindowManagerEx) ToolWindowManager.getInstance(this.myProject)).addToolWindowManagerListener(new ToolWindowManagerListener() {
            @Override
            public void toolWindowRegistered(@NotNull String s) {

            }

            @Override
            public void stateChanged() {
                ToolWindow window = ToolWindowManager.getInstance(myProject).getToolWindow(FreelineToolWindowFactory.TOOL_WINDOW_ID);
                if (window != null) {
                    boolean visible = window.isVisible();
                    if (visible && toolWindow.getContentManager().getContentCount() == 0) {
                        initTerminal(window);
                    }
                }
            }
        });
        toolWindow.show(null);
        JBTabbedTerminalWidget terminalWidget = getTerminalWidget(toolWindow);
        if (terminalWidget != null && terminalWidget.getCurrentSession() != null) {
            Terminal terminal = terminalWidget.getCurrentSession().getTerminal();
            if (terminal != null) {
                terminal.setCursorVisible(false);
            }
        }
    }

    private ToolWindow getToolWindow() {
        return ToolWindowManager.getInstance(myProject).getToolWindow(FreelineToolWindowFactory.TOOL_WINDOW_ID);
    }

    /**
     * 创建Terminal panel
     *
     * @param terminalRunner
     * @param toolWindow
     * @return
     */
    private Content createTerminalInContentPanel(@NotNull AbstractTerminalRunner terminalRunner, @NotNull final ToolWindow toolWindow) {
        SimpleToolWindowPanel panel = new SimpleToolWindowPanel(false, true);
        Content content = ContentFactory.SERVICE.getInstance().createContent(panel, "", false);
        content.setCloseable(true);
        myTerminalWidget = terminalRunner.createTerminalWidget(content);
        panel.setContent(myTerminalWidget.getComponent());
        panel.addFocusListener(this);
        ActionToolbar toolbar = createToolbar(terminalRunner, myTerminalWidget, toolWindow);
        toolbar.setTargetComponent(panel);
        panel.setToolbar(toolbar.getComponent());
        content.setPreferredFocusableComponent(myTerminalWidget.getComponent());
        return content;
    }

    /**
     * 创建左侧工具栏
     *
     * @param terminalRunner
     * @param terminal
     * @param toolWindow
     * @return
     */
    private ActionToolbar createToolbar(@Nullable AbstractTerminalRunner terminalRunner, @NotNull JBTabbedTerminalWidget terminal, @NotNull ToolWindow toolWindow) {
        DefaultActionGroup group = new DefaultActionGroup();
        if (terminalRunner != null) {
            group.add(new RunAction(this));
            group.add(new StopAction(this));
            group.addSeparator();
            group.add(new DebugAction(this));
            group.add(new ForceAction(this));
            group.addSeparator();
            group.add(new ClearAction(this));
        }
        return ActionManager.getInstance().createActionToolbar("unknown", group, false);
    }

    @Override
    public void focusGained(FocusEvent e) {
        JComponent component = myTerminalWidget != null ? myTerminalWidget.getComponent() : null;
        if (component != null) {
            component.requestFocusInWindow();
        }
    }

    @Override
    public void focusLost(FocusEvent e) {

    }

    @Override
    public void projectOpened() {

    }

    @Override
    public void projectClosed() {

    }

    @Override
    public void initComponent() {

    }

    @Override
    public void disposeComponent() {

    }

    @NotNull
    @Override
    public String getComponentName() {
        return "FreelineTerminal";
    }

    /**
     * 停止执行
     */
    private static class StopAction extends BaseTerminalAction {
        private Robot robot;
        public StopAction(FreelineTerminal terminal) {
            super(terminal, "Stop Run Freeline", "Stop Run Freeline", PluginIcons.Suspend);
            try {
                robot = new Robot();
            } catch (AWTException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void doAction(AnActionEvent anActionEvent) {
            if (terminal.getCurrentSession() != null) {
                terminal.getCurrentSession().getComponent().requestFocusInWindow();
                Utils.keyPressWithCtrl(robot, KeyEvent.VK_C);
            }
        }
    }

    private static class RunAction extends BaseTerminalAction {
        String pythonLocation;
        public RunAction(FreelineTerminal terminal) {
            this(terminal, "Run Freeline", "Run Freeline", PluginIcons.FreelineIcon);
        }

        public RunAction(FreelineTerminal terminal, String text, String description, Icon icon) {
            super(terminal, text, description, icon);
        }

        @Override
        public void doAction(AnActionEvent anActionEvent) {
            pythonLocation = Utils.getPythonLocation();
            if (pythonLocation == null) {
                NotificationUtils.pythonNotFound();
            } else {
                terminal.executeShell(getArgs());
            }
        }

        private String[] getArgs() {
            List<String> args = new ArrayList<String>();
            args.add(pythonLocation);
            args.add("freeline.py");
            if (args() != null) {
                args.add(args());
            }
            return args.toArray(new String[]{});
        }

        protected String args() {
            return null;
        }
    }

    private static class DebugAction extends RunAction {
        public DebugAction(FreelineTerminal terminal) {
            super(terminal, "Run Freeline -d", "Run Freeline -d", PluginIcons.StartDebugger);
        }

        @Override
        protected String args() {
            return "-d";
        }
    }

    private static class ForceAction extends RunAction {
        public ForceAction(FreelineTerminal terminal) {
            super(terminal, "Run Freeline -f", "Run Freeline -f", PluginIcons.QuickfixBulb);
        }

        @Override
        protected String args() {
            return "-f";
        }
    }

    /**
     * 清空terminal
     */
    private static class ClearAction extends BaseTerminalAction {
        public ClearAction(FreelineTerminal terminal) {
            super(terminal, "Clear", "Clear", PluginIcons.GC);
        }

        @Override
        public void doAction(AnActionEvent anActionEvent) {
            if (terminal.getCurrentSession() != null) {
                terminal.getCurrentSession().getTerminal().reset();
                terminal.getCurrentSession().getTerminal().setCursorVisible(false);
            }
        }
    }

    private static abstract class BaseTerminalAction extends DumbAwareAction {
        protected FreelineTerminal terminal;

        public BaseTerminalAction(FreelineTerminal terminal, String text, String description, Icon icon) {
            super(text, description, icon);
            this.terminal = terminal;
        }

        @Override
        public void actionPerformed(AnActionEvent anActionEvent) {
            DocumentUtil.saveDocument();
            if (FreelineUtil.checkInstall(anActionEvent.getProject())) {
                doAction(anActionEvent);
            }
        }

        public abstract void doAction(AnActionEvent anActionEvent);
    }
}
