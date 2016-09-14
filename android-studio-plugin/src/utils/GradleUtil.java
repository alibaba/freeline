package utils;

import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.gradle.project.GradleSyncListener;
import com.android.tools.idea.gradle.task.AndroidGradleTaskManager;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.externalSystem.action.ExternalSystemActionUtil;
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.model.execution.ExternalTaskExecutionInfo;
import com.intellij.openapi.externalSystem.model.project.ExternalConfigPathAware;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemNotificationManager;
import com.intellij.openapi.externalSystem.service.notification.NotificationCategory;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.externalSystem.service.notification.NotificationSource;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.externalSystem.view.ExternalSystemNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.execution.ParametersListUtil;
import org.gradle.cli.CommandLineArgumentException;
import org.gradle.cli.CommandLineParser;
import org.gradle.cli.ParsedCommandLine;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.service.execution.cmd.GradleCommandLineOptionsConverter;
import org.jetbrains.plugins.gradle.service.task.ExecuteGradleTaskHistoryService;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.util.*;

/**
 * Created by pengwei on 16/9/13.
 */
public final class GradleUtil {

    /**
     * gradle sync
     * @param project
     * @param listener
     */
    public static void startSync(Project project, GradleSyncListener listener) {
        GradleProjectImporter.getInstance().requestProjectSync(project, listener);
    }

    /**
     * 执行task
     * @param project
     * @param taskName
     * @param args
     * @param listener
     */
    public static void executeTask(Project project, String taskName, String args, ExternalSystemTaskNotificationListener listener) {
        AndroidGradleTaskManager manager = new AndroidGradleTaskManager();
        List<String> taskNames = new ArrayList<>();
        if (taskName != null) {
            taskNames.add(taskName);
        }
        List<String> vmOptions = new ArrayList<>();
        List<String> params = new ArrayList<>();
        if (args != null) {
            params.add(args);
        }
        manager.executeTasks(
                ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, project),
                taskNames, project.getBasePath(), null, vmOptions, params, null, listener);
    }
    /**
     * 执行task
     * 所在action类需要继承GradleExecuteTaskAction
     *
     * @param e
     * @param taskName
     */
    public static void executeTask(AnActionEvent e, String taskName) {
        Project project = e.getRequiredData(CommonDataKeys.PROJECT);
        ExecuteGradleTaskHistoryService historyService = ExecuteGradleTaskHistoryService.getInstance(project);
        String workingDirectory = obtainAppropriateWorkingDirectory(e);
        String fullCommandLine = taskName;
        historyService.addCommand(fullCommandLine, workingDirectory);
        ExternalTaskExecutionInfo taskExecutionInfo;
        try {
            taskExecutionInfo = buildTaskInfo(workingDirectory, fullCommandLine);
        } catch (CommandLineArgumentException var12) {
            NotificationData configuration = new NotificationData("<b>Command-line arguments cannot be parsed</b>", "<i>" + fullCommandLine + "</i> \n" + var12.getMessage(), NotificationCategory.WARNING, NotificationSource.TASK_EXECUTION);
            configuration.setBalloonNotification(true);
            ExternalSystemNotificationManager.getInstance(project).showNotification(GradleConstants.SYSTEM_ID, configuration);
            return;
        }
        RunManagerEx runManager = RunManagerEx.getInstanceEx(project);
        ExternalSystemUtil.runTask(taskExecutionInfo.getSettings(), taskExecutionInfo.getExecutorId(), project, GradleConstants.SYSTEM_ID);
        RunnerAndConfigurationSettings settings = ExternalSystemUtil.createExternalSystemRunnerAndConfigurationSettings(taskExecutionInfo.getSettings(), project, GradleConstants.SYSTEM_ID);
        if (settings != null) {
            RunnerAndConfigurationSettings existingConfiguration = runManager.findConfigurationByName(settings.getName());
            if (existingConfiguration == null) {
                runManager.setTemporaryConfiguration(settings);
            } else {
                runManager.setSelectedConfiguration(existingConfiguration);
            }
        }
    }

    /**
     * 解析执行命令和参数
     *
     * @param projectPath
     * @param fullCommandLine
     * @return
     * @throws CommandLineArgumentException
     */
    private static ExternalTaskExecutionInfo buildTaskInfo(@NotNull String projectPath, @NotNull String fullCommandLine) throws CommandLineArgumentException {
        CommandLineParser gradleCmdParser = new CommandLineParser();
        GradleCommandLineOptionsConverter commandLineConverter = new GradleCommandLineOptionsConverter();
        commandLineConverter.configure(gradleCmdParser);
        ParsedCommandLine parsedCommandLine = gradleCmdParser.parse(ParametersListUtil.parse(fullCommandLine, true));
        Map optionsMap = commandLineConverter.convert(parsedCommandLine, new HashMap());
        List systemProperties = (List) optionsMap.remove("system-prop");
        String vmOptions = systemProperties == null ? "" : StringUtil.join(systemProperties, (entry) -> {
            return "-D" + entry;
        }, " ");
        String scriptParameters = StringUtil.join(optionsMap.entrySet(), (entry) -> {
            Map.Entry<String, Collection> entryValue = (Map.Entry<String, Collection>) entry;
            List values = (List) entryValue.getValue();
            String longOptionName = entryValue.getKey();
            return values != null && !values.isEmpty() ? StringUtil.join(values, (entry1) -> {
                return "--" + longOptionName + ' ' + entry1;
            }, " ") : "--" + longOptionName;
        }, " ");
        List tasks = parsedCommandLine.getExtraArguments();
        ExternalSystemTaskExecutionSettings settings = new ExternalSystemTaskExecutionSettings();
        settings.setExternalProjectPath(projectPath);
        settings.setTaskNames(tasks);
        settings.setScriptParameters(scriptParameters);
        settings.setVmOptions(vmOptions);
        settings.setExternalSystemIdString(GradleConstants.SYSTEM_ID.toString());
        return new ExternalTaskExecutionInfo(settings, DefaultRunExecutor.EXECUTOR_ID);
    }

    /**
     * 获取工作目录
     *
     * @param e
     * @return
     */
    private static String obtainAppropriateWorkingDirectory(AnActionEvent e) {
        List selectedNodes = (List) ExternalSystemDataKeys.SELECTED_NODES.getData(e.getDataContext());
        if (selectedNodes != null && selectedNodes.size() == 1) {
            ExternalSystemNode node1 = (ExternalSystemNode) selectedNodes.get(0);
            Object externalData1 = node1.getData();
            if (externalData1 instanceof ExternalConfigPathAware) {
                return ((ExternalConfigPathAware) externalData1).getLinkedExternalProjectPath();
            } else {
                ExternalConfigPathAware parentExternalConfigPathAware = (ExternalConfigPathAware) node1.findParentData(ExternalConfigPathAware.class);
                return parentExternalConfigPathAware != null ? parentExternalConfigPathAware.getLinkedExternalProjectPath() : "";
            }
        } else {
            Module node = ExternalSystemActionUtil.getModule(e.getDataContext());
            String externalData = ExternalSystemApiUtil.getExternalProjectPath(node);
            return externalData == null ? "" : externalData;
        }
    }
}
