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

        // TODO: 16-9-5  Android SDK环境检测、Python环境检测

        File dir = new File(currentProject.getBasePath());
        File file = new File(dir, "freeline.py");

        // 已经初始化过了，直接执行
        if (file.exists()) {
//            execCmd("python freeline.py");
            new Thread(new ActionRunnabe(dir, file, e)).start();

        } else {
            // 未初始化，先下载下来
            // for *unix
            execCmd("./gradlew initFreeline -Pmirror", dir, true);
        }
    }

    /**
     * 执行指定命令，是否开启新的线程执行异步操作
     */
    private void execCmd(final String command, final File dir, boolean async) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                execCmd(command, dir);
            }
        };

        // 新开一个线程异步执行
        if (async) {
            new Thread(runnable).start();
        } else {
            runnable.run();
        }
    }

    /**
     * 执行命令行
     */
    private void execCmd(String command, File dir) {
        try {
            Process p = Runtime.getRuntime().exec(command, null, dir);
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

    private void showTip() {

    }
}
