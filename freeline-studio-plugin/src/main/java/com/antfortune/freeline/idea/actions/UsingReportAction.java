package com.antfortune.freeline.idea.actions;

import com.android.tools.idea.gradle.parser.GradleBuildFile;
import com.antfortune.freeline.idea.icons.PluginIcons;
import com.antfortune.freeline.idea.utils.GradleUtil;
import com.antfortune.freeline.idea.utils.NotificationUtils;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiFile;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by huangyong on 17/2/14.
 */
public class UsingReportAction extends AnAction implements OnRequestCallback {

    public UsingReportAction() {
        super(PluginIcons.FreelineIcon);
    }

    @Override
    public void actionPerformed(AnActionEvent event) {
        final Project project = event.getProject();
        Module[] modules = ModuleManager.getInstance(project).getModules();
        List<Pair<Module, PsiFile>> selectModulesList = new ArrayList<Pair<Module, PsiFile>>();
        for (Module module : modules) {
            GradleBuildFile file = GradleBuildFile.get(module);
            if (file != null && !GradleUtil.isLibrary(file)) {
                selectModulesList.add(Pair.create(module, file.getPsiFile()));
            }
        }

        if (selectModulesList.size() > 1) {
            final DialogBuilder builder = new DialogBuilder();
            builder.setTitle("Freeline Reporter");
            builder.resizable(false);
            builder.setCenterPanel(new JLabel("There are multiple application modules, Please select the exact one.",
                    Messages.getInformationIcon(), SwingConstants.CENTER));
            builder.addOkAction().setText("Cancel");
            for (final Pair<Module, PsiFile> pair : selectModulesList) {
                builder.addAction(new AbstractAction(":" + pair.first.getName()) {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        builder.getDialogWrapper().close(DialogWrapper.CANCEL_EXIT_CODE);
                        report(project, pair.getSecond());
                    }
                });
            }
            if (builder.show() > -1) {
                //return false;
            }
        } else if (selectModulesList.size() == 1) {
            report(project, selectModulesList.get(0).getSecond());
        }
    }

    private void report(Project project, PsiFile psiFile) {
        Module module = ModuleUtilCore.findModuleForFile(psiFile.getVirtualFile(), project);
        assert module != null;
        AndroidFacet facet = AndroidFacet.getInstance(module);
        assert facet != null;

        Manifest manifest = facet.getManifest();
        if (manifest == null) {
            NotificationUtils.errorNotification("manifest file is null.");
            return;
        }

        String packageName = manifest.getPackage().getValue();
        if (packageName != null && packageName.length() > 0) {
            UsingReportAsync task = new UsingReportAsync(packageName, this);
            ApplicationManager.getApplication().executeOnPooledThread(task);
        }
    }

    @Override
    public void onSuccess() {
        NotificationUtils.infoNotification("Enjoy your coding time with freeline!");
    }

    @Override
    public void onFailure(Exception e) {

    }

    private static class UsingReportAsync implements Runnable {

        private String packageName;

        private OnRequestCallback callback;

        public UsingReportAsync(String packageName, OnRequestCallback callback) {
            this.packageName = packageName;
            this.callback = callback;
        }

        @Override
        public void run() {
            try {
                URL url = new URL("https://www.freelinebuild.com/api/feedback/app");
//                URL url = new URL("http://localhost:3000/api/feedback/app");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setReadTimeout(10000);
                conn.setConnectTimeout(15000);
                conn.setRequestMethod("POST");
                conn.setDoInput(true);
                conn.setDoOutput(true);

                StringBuilder builder = new StringBuilder();
                builder.append(URLEncoder.encode("pkg", "UTF-8"));
                builder.append("=");
                builder.append(URLEncoder.encode(packageName, "UTF-8"));

                OutputStream os = conn.getOutputStream();
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
                writer.write(builder.toString());
                writer.flush();
                writer.close();
                os.close();

                int responseCode = conn.getResponseCode();
                if (responseCode >= 400) {
                    this.callback.onFailure(new Exception(conn.getResponseMessage()));
                } else {
                    this.callback.onSuccess();
                }
                conn.disconnect();
            } catch (IOException e) {
                this.callback.onFailure(e);
            }
        }
    }
}
