package views;

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
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.terminal.ui.JediTermWidget;
import icons.PluginIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.terminal.AbstractTerminalRunner;
import org.jetbrains.plugins.terminal.JBTabbedTerminalWidget;
import org.jetbrains.plugins.terminal.LocalTerminalDirectRunner;
import utils.NotificationUtils;
import utils.Utils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;

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

    public JBTabbedTerminalWidget getTerminalWidget() {
        ToolWindow window = getToolWindow();
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

    public void initAndExecute(String[] shell) {
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
    private static class StopAction extends DumbAwareAction {
        private FreelineTerminal terminal;
        private Robot robot;

        public StopAction(FreelineTerminal terminal) {
            super("Stop Run Freeline", "Stop Run Freeline", PluginIcons.Suspend);
            this.terminal = terminal;
            try {
                robot = new Robot();
            } catch (AWTException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void actionPerformed(AnActionEvent anActionEvent) {
            if (terminal.getCurrentSession() != null) {
                terminal.getCurrentSession().getComponent().requestFocusInWindow();
                Utils.keyPressWithCtrl(robot, KeyEvent.VK_C);
            }
        }
    }

    private static class RunAction extends DumbAwareAction {
        private FreelineTerminal terminal;

        public RunAction(FreelineTerminal terminal) {
            super("Run Freeline", "Run Freeline", PluginIcons.FreelineIcon);
            this.terminal = terminal;
        }

        @Override
        public void actionPerformed(AnActionEvent anActionEvent) {
            terminal.executeShell(new String[]{Utils.getPythonLocation(), "freeline.py"});
        }
    }

    private static class DebugAction extends DumbAwareAction {
        private FreelineTerminal terminal;

        public DebugAction(FreelineTerminal terminal) {
            super("Run Freeline -d", "Run Freeline -d", PluginIcons.StartDebugger);
            this.terminal = terminal;
        }

        @Override
        public void actionPerformed(AnActionEvent anActionEvent) {
            terminal.executeShell(new String[]{Utils.getPythonLocation(), "freeline.py", "-d"});
        }
    }

    private static class ForceAction extends DumbAwareAction {
        private FreelineTerminal terminal;

        public ForceAction(FreelineTerminal terminal) {
            super("Run Freeline -f", "Run Freeline -f", PluginIcons.QuickfixBulb);
            this.terminal = terminal;
        }

        @Override
        public void actionPerformed(AnActionEvent anActionEvent) {
            terminal.executeShell(new String[]{Utils.getPythonLocation(), "freeline.py", "-f"});
        }
    }

    /**
     * 清空terminal
     */
    private static class ClearAction extends DumbAwareAction {
        private FreelineTerminal terminal;

        public ClearAction(FreelineTerminal terminal) {
            super("Clear", "Clear", PluginIcons.GC);
            this.terminal = terminal;
        }

        @Override
        public void actionPerformed(AnActionEvent anActionEvent) {
            if (terminal.getCurrentSession() != null) {
                terminal.getCurrentSession().getTerminal().reset();
            }
        }
    }
}
