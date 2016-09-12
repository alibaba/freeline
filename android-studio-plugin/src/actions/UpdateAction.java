package actions;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.wm.WindowManager;
import models.GradleVersionEntity;
import org.apache.commons.io.FileUtils;
import utils.NotificationUtils;
import utils.StreamUtil;
import views.UpdateDialog;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by pengwei on 16/9/11.
 */
public class UpdateAction extends BaseAction {

    private List<File> gradleFileList = new ArrayList<>();
    public static String[] excludeFolder = {".idea", ".git", ".svn", "build", "gradle"};
    private String reg = "(classpath 'com.antfortune.freeline:gradle:.+?'|\\w+?ompile 'com.antfortune.freeline:runtime.+?')";
    private Pattern p = Pattern.compile(reg);
    private String localVersion;

    @Override
    public void actionPerformed() {
        if (checkFreeLineExist()) {
            ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
                @Override
                public void run() {
                    getLocalGradleVersion();
                    String result = getLastVersion();
                    if (result != null) {
                        GradleVersionEntity entity = GradleVersionEntity.parse(result);
                        versionUpdate(entity);
                    } else {
                        NotificationUtils.errorNotification("check update failure");
                    }
                }
            });
        }
    }

    /**
     * 获取本地gradle文件
     *
     * @return
     */
    private void getLocalGradleVersion() {
        gradleFileList.clear();
        File root = projectDir;
        if (root != null) {
            getFile(root, 0);
        }
    }

    /**
     * 列出1-2级目录
     *
     * @param file
     * @param level
     */
    private void getFile(File file, int level) {
        if (level > 2) {
            return;
        }
        if (file.getName().equals("build.gradle")) {
            gradleFileList.add(file);
            return;
        }
        for (String exclude : excludeFolder) {
            if (exclude.equals(file.getName())) {
                return;
            }
        }
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            for (File cFile : files) {
                getFile(cFile, level + 1);
            }
        }
    }

    /**
     * 提示新版本
     *
     * @param entity
     */
    private void versionUpdate(GradleVersionEntity entity) {
        localVersion = null;
        final Map<String, String> map = new HashMap<>();
        if (entity == null || entity.getVersion() == null) {
            NotificationUtils.errorNotification("check update failure.");
            return;
        }

        for (File file : gradleFileList) {
            StringBuilder builder = new StringBuilder();
            try {
                String fileContent = FileUtils.readFileToString(file);
                Matcher m = p.matcher(fileContent);
                while (m.find()) {
                    if (m.group(1).contains("com.antfortune.freeline:gradle:")) {
                        localVersion = m.group(1).split(":")[2].replace("'", "");
                    }
                    builder.append(m.group(1)).append("\n");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (builder.toString().trim() != "") {
                map.put(file.getPath(), builder.toString());
            }
        }
        if (localVersion == null) {
            NotificationUtils.errorNotification("please install FreeLine first");
            return;
        }
        int res = localVersion.compareTo(entity.getVersion());
        if (res < 0) {
            UpdateDialog dialog = new UpdateDialog();
            dialog.setOkActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    updateLocal(entity.getVersion(), localVersion, map.keySet());
                    dialog.dispose();
                }
            });
            dialog.pack();
            dialog.setLocationRelativeTo(WindowManager.getInstance().getFrame(anActionEvent.getProject()));
            dialog.setServerVersion(entity.getGroupId() + ":" + entity.getArtifactId() + ":" + entity.getVersion());
            for (String key : map.keySet()) {
                dialog.addGradleContent(key, map.get(key));
            }
            dialog.setVisible(true);
        } else if (res > 0) {
            NotificationUtils.infoNotification("Your version seems too high");
        } else {
            NotificationUtils.infoNotification("Local version is up to date");
        }
    }

    /**
     * 更新本地版本
     *
     * @param newVersion
     * @param oldVersion
     * @param fileList
     */
    private void updateLocal(String newVersion, String oldVersion, Set<String> fileList) {
        for (String file : fileList) {
            File gradleFile = new File(file);
            if (gradleFile.exists()) {
                try {
                    String content = FileUtils.readFileToString(gradleFile);
                    content = content.replaceAll("'com.antfortune.freeline:gradle:.+?'", "'com.antfortune.freeline:gradle:" + newVersion + "'")
                            .replaceAll("\\w+?ompile 'com.antfortune.freeline:runtime.+?'", "");
                    FileUtils.writeStringToFile(gradleFile, content, false);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        NotificationUtils.infoNotification("freeline gradle update success");
    }

    /**
     * 获取最后的版本
     *
     * @return
     */
    private String getLastVersion() {
        try {
            URL url = new URL("http://jcenter.bintray.com/com/antfortune/freeline/gradle/maven-metadata.xml");
            HttpURLConnection conn = null;
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5 * 1000);
            conn.setRequestMethod("GET");
            InputStream inStream = conn.getInputStream();
            String result = StreamUtil.inputStream2String(inStream);
            return result;
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
