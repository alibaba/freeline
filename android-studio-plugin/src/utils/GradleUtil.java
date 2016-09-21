package utils;

import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.gradle.project.GradleSyncListener;
import com.android.tools.idea.gradle.task.AndroidGradleTaskManager;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import com.intellij.openapi.project.Project;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.util.*;

/**
 * Created by pengwei on 16/9/13.
 */
public final class GradleUtil {

    /**
     * gradle sync
     *
     * @param project
     * @param listener
     */
    public static void startSync(Project project, GradleSyncListener listener) {
        GradleProjectImporter.getInstance().requestProjectSync(project, listener);
    }

    /**
     * 执行task
     *
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
        if (listener == null) {
            listener = new ExternalSystemTaskNotificationListenerAdapter() {
            };
        }
        manager.executeTasks(
                ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, project),
                taskNames, project.getBasePath(), null, vmOptions, params, null, listener);
    }
}
