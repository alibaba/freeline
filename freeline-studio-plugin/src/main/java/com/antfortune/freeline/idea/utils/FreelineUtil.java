package com.antfortune.freeline.idea.utils;

import com.antfortune.freeline.idea.actions.UpdateAction;

import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencySpec;
import com.android.tools.idea.gradle.parser.GradleBuildFile;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiFile;
import com.intellij.ui.content.impl.ContentImpl;
import com.antfortune.freeline.idea.icons.PluginIcons;
import com.antfortune.freeline.idea.models.*;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

import java.util.*;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Freeline Utility
 *
 * @author act262@gmail.com
 */
public class FreelineUtil {

    // TODO: 2016/9/13 0013 need refactor tool window
    private final static String TOOL_ID = "Freeline Console";

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
    // TODO: 2016/9/14 0014 need refactor console method
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
        toolWindow.setIcon(PluginIcons.ICON_TOOL_WINDOW);
        toolWindow.getContentManager().addContent(new ContentImpl(consoleView.getComponent(), "Build", true));
        toolWindow.show(null);
    }

    /**
     * if had init freeline return true
     */
    public static boolean hadInitFreeline(Project project) {
        if (project != null) {
            String projectPath = project.getBasePath();
            // freeline directory
            File freelineDir = new File(projectPath, "freeline");
            // freeline.py file
            File freeline_py = new File(projectPath, "freeline.py");
            if (freelineDir.exists() && freeline_py.exists()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 是否载入freeline
     *
     * @param project
     * @return
     */
    public static boolean hasInitFreeline(@NotNull Project project) {
        return getFreelineStatus(project).hasInitFreeline();
    }

    /**
     * 获取freeline安装状态
     *
     * @param project
     * @return
     */
    public static FreelineStatus getFreelineStatus(@NotNull Project project) {
        FreelineStatus status = new FreelineStatus();
        Collection<VirtualFile> gradleFiles = GradleUtil.getAllGradleFile(project);
        status.setGradleBuildFiles(gradleFiles);
        for (VirtualFile file : gradleFiles) {
            if (!status.isExistClasspath()) {
                GradleBuildModel model = GradleBuildModel.parseBuildFile(file, project);
                if (model != null) {
                    List<ArtifactDependencyModel> classPaths = model.buildscript().dependencies().artifacts();
                    for (ArtifactDependencyModel classpath : classPaths) {
                        ArtifactDependencyModelWrapper wrapper = new ArtifactDependencyModelWrapper(classpath);
                        if (wrapper.group().equals(Constant.FREELINE_CLASSPATH_GROUP)
                                && wrapper.name().equals(Constant.FREELINE_CLASSPATH_ARTIFACT)) {
                            status.setClasspathFile(file);
                            break;
                        }
                    }
                }
            }
            // 正则二次判断是否存在Freeline classpath
            if (!status.isExistClasspath() && regularExistFreelineClassPath(file)) {
                status.setClasspathFile(file);
            }
            if (!status.isExistPlugin()) {
                GradleBuildFile gradleBuildFile = new GradleBuildFile(file, project);
                if (gradleBuildFile != null) {
                    List<String> plugins = gradleBuildFile.getPlugins();
                    if (plugins.contains(Constant.FREELINE_PLUGIN_ID)) {
                        status.setPluginFile(file);
                    }
                }
            }
            if (status.isExistClasspath() && status.isExistPlugin()) {
                break;
            }
        }
        File baseFile = new File(project.getBasePath());
        if (new File(baseFile, Constant.FREELINE_ROOT_FOLDER).exists()
                && new File(baseFile, Constant.FREELINE_PYTHON).exists()) {
            if (SystemInfo.isWindows) {
                if (new File(baseFile, Constant.FREELINE_ROOT_FOLDER_CORE).exists()) {
                    status.setExistFreelineCore(true);
                }
            } else {
                status.setExistFreelineCore(true);
            }
        }
        return status;
    }

    /**
     * 检查是否需要载入Freeline
     *
     * @param project
     * @return
     */
    public static boolean checkInstall(@NotNull final Project project) {
        final FreelineStatus status = getFreelineStatus(project);
        if (GradleUtil.isSyncInProgress(project)) {
            NotificationUtils.errorMsgDialog("Waiting for sync project to complete");
            return false;
        }
        if (status.hasInitFreeline()) {
            return true;
        }
        if (status.getGradleBuildFiles().size() < 1) {
            NotificationUtils.errorMsgDialog("It's not an Android Gradle project Currently?");
            return false;
        }
        if (status.isExistClasspath() && status.isExistPlugin() && !status.isExistFreelineCore()) {
            NotificationUtils.errorNotification("Execute task initFreeline and download freeline dependencies...");
            initFreeline(project);
            return false;
        }
        if (DialogUtil.createDialog("Detected that you did not installFreeline Freeline, Whether installFreeline Automatically？",
                "Install Freeline Automatically", "Cancel")) {
            Module[] modules = ModuleManager.getInstance(project).getModules();
            List<Pair<Module, PsiFile>> selectModulesList = new ArrayList<Pair<Module, PsiFile>>();
            for (Module module : modules) {
                GradleBuildFile file = GradleBuildFile.get(module);
                if (file != null && !GradleUtil.isLibrary(file)) {
                    selectModulesList.add(Pair.create(module, file.getPsiFile()));
                }
            }
            // 多个app模块的情况
            if (selectModulesList.size() > 1) {
                final DialogBuilder builder = new DialogBuilder();
                builder.setTitle("Install Freeline");
                builder.resizable(false);
                builder.setCenterPanel(new JLabel("There are multiple application modules, Please select the module to be installed Freeline.",
                        Messages.getInformationIcon(), SwingConstants.CENTER));
                builder.addOkAction().setText("Cancel");
                for (final Pair<Module, PsiFile> pair : selectModulesList) {
                    builder.addAction(new AbstractAction(":" + pair.first.getName()) {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            builder.getDialogWrapper().close(DialogWrapper.CANCEL_EXIT_CODE);
                            installFreeline(project, status, pair.getSecond());
                        }
                    });
                }
                if (builder.show() > -1) {
                    return false;
                }
            } else if (selectModulesList.size() == 1) {
                installFreeline(project, status, selectModulesList.get(0).getSecond());
            } else {
                NotificationUtils.errorMsgDialog("Can not found Application Module! Please Sync Project.");
                return false;
            }
        }
        return false;
    }

    /**
     * 载入Freeline
     *
     * @param project
     * @param status
     * @param psiFile
     */
    private static void installFreeline(final Project project, final FreelineStatus status, final PsiFile psiFile) {
        ApplicationManager.getApplication().executeOnPooledThread(new UpdateAction.GetServerVersion(new GetServerCallback() {
            @Override
            public void onSuccess(final GradleDependencyEntity entity) {
                LogUtil.d("获取版本号成功:" + entity);
                ApplicationManager.getApplication().invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        installFreeline(project, status, psiFile, entity);
                    }
                });
            }

            @Override
            public void onFailure(String errMsg) {
                LogUtil.d("获取版本号失败:" + errMsg);
                NotificationUtils.errorNotification("Get Freeline Version Failure: " + errMsg);
            }
        }));
    }

    private static boolean needReformatCode = false;

    private static void installFreeline(final Project project, final FreelineStatus status, final PsiFile psiFile,
                                        final GradleDependencyEntity dependencyEntity) {
        needReformatCode = false;
        CommandProcessor.getInstance().runUndoTransparentAction(new Runnable() {
            @Override
            public void run() {
                ApplicationManager.getApplication().runWriteAction(new Runnable() {
                    @Override
                    public void run() {
                        if (!status.isExistClasspath()) {
                            Collection<VirtualFile> collection = status.getGradleBuildFiles();
                            if (dependencyEntity != null) {
                                for (VirtualFile file : collection) {
                                    GradleBuildModel model = GradleBuildModel.parseBuildFile(file, project);
                                    List<ArtifactDependencyModel> artifactDependencyModels = model.buildscript().dependencies().artifacts();
                                    for (ArtifactDependencyModel model1 : artifactDependencyModels) {
                                        ArtifactDependencyModelWrapper wrapper = new ArtifactDependencyModelWrapper(model1);
                                        if (wrapper.group().equals(Constant.ANDROID_GRADLE_TOOL_GROUP_NAME)) {
                                            ArtifactDependencySpec spec = new ArtifactDependencySpec(dependencyEntity.getArtifactId(),
                                                    dependencyEntity.getGroupId(), dependencyEntity.getNewestReleaseVersion());
                                            model.buildscript().dependencies().addArtifact("classpath", spec);
                                            model.applyChanges();
                                            needReformatCode = true;
                                            status.setClasspathFile(file);
                                            break;
                                        }
                                    }
                                    if (status.isExistClasspath()) {
                                        break;
                                    }
                                }
                            }
                        }
                        if (!status.isExistPlugin()) {
                            if (psiFile != null && psiFile instanceof GroovyFile) {
                                GradleUtil.applyPlugin(project, (GroovyFile) psiFile, Constant.FREELINE_PLUGIN_ID);
                            }
                        }
                    }
                });
            }
        });
        if (needReformatCode && status.getClasspathFile() != null) {
            DocumentUtil.reformatCode(project, status.getClasspathFile());
        }
        LogUtil.d("Sync Project Finish, start download freeline.zip.");
        initFreeline(project);
    }

    public static final Pattern PATTERN_CLASSPATH = Pattern.compile("classpath\\s+'"
            + Constant.FREELINE_CLASSPATH_GROUP + ":" + Constant.FREELINE_CLASSPATH_ARTIFACT + ":[\\d|\\.]*'");

    /**
     * 正则二次判断是否存在Freeline classpath
     *
     * @param file
     * @return
     */
    public static boolean regularExistFreelineClassPath(VirtualFile file) {
        try {
            if (file.exists()) {
                String content = FileUtils.readFileToString(new File(file.getPath()));
                Matcher matcher = PATTERN_CLASSPATH.matcher(content);
                return matcher.find();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 执行./gradlew initFreeline
     * @param project
     */
    public static void initFreeline(Project project) {
        GradleUtil.executeTask(project, "initFreeline", "-Pmirror", new ExternalSystemTaskNotificationListenerAdapter() {
            @Override
            public void onTaskOutput(@NotNull ExternalSystemTaskId id, @NotNull String text, boolean stdOut) {
                super.onTaskOutput(id, text, stdOut);
            }
        });
    }
}
