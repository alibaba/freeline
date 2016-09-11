package com.antfortune.freeline.plugin.actions;

import com.antfortune.freeline.plugin.runnable.BuildRunnable;
import com.antfortune.freeline.plugin.utils.SystemUtil;
import com.antfortune.freeline.plugin.utils.Util;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;

import java.io.File;
import java.util.ArrayList;

/**
 * build or init freeline action
 *
 * @author act262@gmail.com
 */
public class BuildAction extends AnAction {

    public static void run(File dir, AnActionEvent e) {
        File file = new File(dir, "freeline.py");
        // 已经初始化过了，直接执行
        if (file.exists()) {
            // python freeline.py
            new Thread(new BuildRunnable(dir, file, e)).start();
        } else {
            // 未初始化，先下载下来
            initFreeline(dir);
        }
    }

    public static void initFreeline(File dir) {
        ArrayList<String> cmds = new ArrayList<>();
        // for windows
        if (SystemUtil.isWindows()) {
            // gradlew.bat initFreeline -Pmirror
            cmds.add("gradlew.bat");
            cmds.add("initFreeline");
            cmds.add("-Pmirror");
        } else {
            // for *unix
            // ./gradlew initFreeline -Pmirror
            cmds.add("./gradlew");
            cmds.add("initFreeline");
            cmds.add("-Pmirror");
        }
        Util.execCmd(cmds, dir, true);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project currentProject = DataKeys.PROJECT.getData(e.getDataContext());
        FileDocumentManager.getInstance().saveAllDocuments();

        // TODO: 16-9-5  Android SDK环境检测、Python环境检测
//        if (!SystemUtil.hasPython()) {
//            System.out.println("Not found python");
//            return;
//        }
//        if (!SystemUtil.hasAndroidSDK()) {
//            System.out.println("Not found Android_HOME");
//            return;
//        }

        File dir = new File(currentProject.getBasePath());

        run(dir, e);
    }

    private void showTip() {
    }
}