import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * Created by zcx on 16-9-5.
 */
public class RunFreelineAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project currentProject = DataKeys.PROJECT.getData(e.getDataContext());
        FileDocumentManager.getInstance().saveAllDocuments();

        File dir = new File(currentProject.getBasePath());
        File file = new File(dir, "freeline.py");

        // 已经初始化过了，直接执行
        if (file.exists()) {
//            execCmd("python freeline.py");
            new Thread(new ActionRunnabe(dir, file, e)).start();

        } else {
            // 未初始化，先下载下来
            // for *unix
            execCmd("pwd", dir);
            execCmd("./gradlew initFreeline -Pmirror", dir);
        }
    }

    /**
     * 执行命令行
     */
    private void execCmd(String cmd, File dir) {
        try {
            Process p = Runtime.getRuntime().exec(cmd, null, dir);
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

    private void execCMD(String command, boolean asyn) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {

            }
        };

        // 新开一个线程异步执行
        if (asyn) {
            new Thread(runnable).start();
        } else {
            runnable.run();
        }
    }

    private void showTip() {

    }
}
