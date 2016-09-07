package com.antfortune.freeline.plugin.actions;

import com.antfortune.freeline.plugin.runnable.BuildRunnable;
import com.antfortune.freeline.plugin.utils.SystemUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

/**
 * Created by zcx on 16-9-5.
 */
public class BuildAction extends AnAction {

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
        File file = new File(dir, "freeline.py");

        // 已经初始化过了，直接执行
        if (file.exists()) {
            // python freeline.py
            new Thread(new BuildRunnable(dir, file, e)).start();

        } else {
            // 未初始化，先下载下来
//            FreelineInitializer.initFreeline(Gradle);

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
            execCmd(cmds, dir, true);
        }
    }

    /**
     * 执行指定命令，是否开启新的线程执行异步操作
     */
    private void execCmd(final ArrayList<String> commands, final File dir, boolean async) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                execCmd(commands, dir);
            }
        };

        // 新开一个线程异步执行
        if (async) {
            new Thread(runnable).start();
        } else {
            runnable.run();
        }
    }

    private void execCmd(ArrayList<String> commands, File dir) {
        if (SystemUtil.isWindows()) {
            execCmd(wrapWindowsCmds(commands), dir);
        } else {
            execCmd(wrapUnixCmds(commands), dir);
        }
    }

    /**
     * 执行命令行
     */
    private void execCmd(String[] commands, File dir) {
        try {
            Process p = Runtime.getRuntime().exec(commands, null, dir);
            InputStream ins = p.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int l;
            while ((l = ins.read(buf)) != -1) {
                bos.write(buf, 0, l);
            }
            ins.close();
            if (bos.size() > 0) {
                bos.write('\n');
            }
            ins = p.getErrorStream();
            while ((l = ins.read(buf)) != -1) {
                bos.write(buf, 0, l);
            }
            ins.close();
            int exit = p.waitFor();
            String output = new String(bos.toByteArray());

            // TODO: 16-9-5 提示
            System.out.println("output = " + output);

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    @NotNull
    private String[] wrapUnixCmds(ArrayList<String> cmds) {
        cmds.add(0, "/bin/sh");
        cmds.add(1, "-c");
        return cmds.toArray(new String[0]);
    }

    @NotNull
    private String[] wrapWindowsCmds(ArrayList<String> cmds) {
        cmds.add(0, "cmd");
        cmds.add(1, "/c");
        return cmds.toArray(new String[0]);
    }

    private void showTip() {

    }
}