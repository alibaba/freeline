package com.antfortune.freeline.idea.actions;

import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencyModel;
import com.antfortune.freeline.idea.icons.PluginIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.antfortune.freeline.idea.models.ArtifactDependencyModelWrapper;
import com.antfortune.freeline.idea.models.GradleDependencyEntity;
import com.antfortune.freeline.idea.models.GetServerCallback;
import com.antfortune.freeline.idea.models.GradleSyncHandler;
import org.jetbrains.annotations.NotNull;
import com.antfortune.freeline.idea.utils.*;
import com.antfortune.freeline.idea.views.CheckUpdateDialog;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

/**
 * Created by pengwei on 16/9/11.
 */
public class UpdateAction extends BaseAction implements GetServerCallback {

    public UpdateAction() {
        super(PluginIcons.GradleSync);
    }

    @Override
    public void actionPerformed() {
        if (FreelineUtil.checkInstall(currentProject)) {
            asyncTask(new GetServerVersion(this));
        }
    }

    /**
     * 更新操作
     *
     * @param newVersion
     * @param gradleBuildModels
     */
    protected void updateAction(final String newVersion, final Map<GradleBuildModel,
            List<ArtifactDependencyModel>> gradleBuildModels) {
        CommandProcessor.getInstance().runUndoTransparentAction(new Runnable() {
            @Override
            public void run() {
                ApplicationManager.getApplication().runWriteAction(new Runnable() {
                    @Override
                    public void run() {
                        for (GradleBuildModel file : gradleBuildModels.keySet()) {
                            List<ArtifactDependencyModel> models = gradleBuildModels.get(file);
                            for (ArtifactDependencyModel dependencyModel1 : models) {
                                ArtifactDependencyModelWrapper dependencyModel = new ArtifactDependencyModelWrapper(dependencyModel1);
                                if (isClasspathLibrary(dependencyModel)) {
                                    dependencyModel1.setVersion(newVersion);
                                }
                                if (isDependencyLibrary(dependencyModel)) {
                                    file.dependencies().remove(dependencyModel1);
                                }
                            }
                            file.applyChanges();
                        }
                        GradleUtil.executeTask(currentProject, "initFreeline", "-Pmirror", new ExternalSystemTaskNotificationListenerAdapter() {
                            @Override
                            public void onTaskOutput(@NotNull ExternalSystemTaskId id, @NotNull String text, boolean stdOut) {
                                super.onTaskOutput(id, text, stdOut);
                            }
                        });
                    }
                });
            }
        });
    }

    /**
     * 处理结果
     *
     * @param entity
     * @param gradleBuildModels
     */
    private void resultHandle(final GradleDependencyEntity entity, final Map<GradleBuildModel,
            List<ArtifactDependencyModel>> gradleBuildModels) {
        String localVersion = null;
        StringBuilder builder = new StringBuilder();
        for (GradleBuildModel file : gradleBuildModels.keySet()) {
            List<ArtifactDependencyModel> models = gradleBuildModels.get(file);
            for (ArtifactDependencyModel dependencyModel1 : models) {
                ArtifactDependencyModelWrapper dependencyModel = new ArtifactDependencyModelWrapper(dependencyModel1);
                if (isClasspathLibrary(dependencyModel) || isDependencyLibrary(dependencyModel)) {
                    if (isClasspathLibrary(dependencyModel)) {
                        localVersion = dependencyModel.version();
                    }
                    builder.append(dependencyModel.configurationName()).append(" '")
                            .append(dependencyModel.group()).append(":")
                            .append(dependencyModel.name()).append(":")
                            .append(dependencyModel.version()).append("'")
                            .append("<br/>");
                }
            }
        }
        if (Utils.notEmpty(localVersion)) {
            int compare = localVersion.compareTo(entity.getVersion());
            final CheckUpdateDialog dialog = new CheckUpdateDialog();
            dialog.getButtonOK().setEnabled(compare < 0);
            dialog.setServerVersion(entity.getGroupId(), entity.getArtifactId(), entity.getVersion());
            dialog.setServerUpdateTime(entity.getUpdateTime());
            dialog.setLocalVersion(builder.toString());
            dialog.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    updateAction(entity.getVersion(), gradleBuildModels);
                    dialog.dispose();
                }
            });
            dialog.showDialog();
        } else {
            NotificationUtils.infoNotification("please add freeline dependency first");
        }
    }

    private boolean isClasspathLibrary(ArtifactDependencyModelWrapper model) {
        return model.configurationName().equals("classpath") &&
                model.group().equals("com.antfortune.freeline") && model.name().equals("gradle");
    }

    private boolean isDependencyLibrary(ArtifactDependencyModelWrapper model) {
        return model.configurationName().endsWith("ompile") &&
                model.group().equals("com.antfortune.freeline") && model.name().startsWith("runtime");
    }

    @Override
    public void onSuccess(final GradleDependencyEntity entity) {
        invokeLater(new Runnable() {
            @Override
            public void run() {
                Collection<VirtualFile> gradleFiles = GradleUtil.getAllGradleFile(currentProject);
                Map<GradleBuildModel, List<ArtifactDependencyModel>> fileListMap = new HashMap<GradleBuildModel, List<ArtifactDependencyModel>>();
                for (VirtualFile file : gradleFiles) {
                    GradleBuildModel model = GradleBuildModel.parseBuildFile(file, currentProject);
                    if (model != null) {
                        List<ArtifactDependencyModel> classPaths = model.buildscript().dependencies().artifacts();
                        List<ArtifactDependencyModel> depends = model.dependencies().artifacts();
                        classPaths.addAll(depends);
                        fileListMap.put(model, classPaths);
                    }
                }
                resultHandle(entity, fileListMap);
            }
        });
    }

    @Override
    public void onFailure(String errMsg) {
        NotificationUtils.errorNotification("Update Freeline Failure: " + errMsg);
    }

    /**
     * 获取服务器最新版本
     */
    public static class GetServerVersion implements Runnable {
        private GetServerCallback callback;

        public GetServerVersion(GetServerCallback callback) {
            this.callback = callback;
        }

        @Override
        public void run() {
            if (this.callback == null) {
                return;
            }
            try {
                URL url = new URL("http://jcenter.bintray.com/com/antfortune/freeline/gradle/maven-metadata.xml");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5 * 1000);
                conn.setRequestMethod("GET");
                InputStream inStream = conn.getInputStream();
                String result = StreamUtil.inputStream2String(inStream);
                if (result != null && result.trim().length() != 0) {
                    GradleDependencyEntity entity = GradleDependencyEntity.parse(result);
                    if (entity != null && Utils.notEmpty(entity.getVersion())
                            && Utils.notEmpty(entity.getGroupId())) {
                        this.callback.onSuccess(entity);
                    } else {
                        this.callback.onFailure("analytic failure");
                    }
                } else {
                    this.callback.onFailure("response empty");
                }
            } catch (IOException e) {
                this.callback.onFailure(e.getMessage());
            }
        }
    }
}
