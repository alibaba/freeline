package actions;

import utils.NotificationUtils;
import utils.Utils;

import java.io.File;

/**
 * Created by pengwei on 16/9/11.
 */
public class FreeLineRunAction extends BaseAction {
    @Override
    public void actionPerformed() {
        File pyFile = new File(projectDir, "freeline.py");
        if (pyFile.exists()) {
            String python = Utils.getPythonLocation();
            if (python == null) {
                NotificationUtils.errorNotification("command 'python' not found");
            } else {
                executeShell(new String[]{python, pyFile.getPath(), getArgs()});
            }
        } else {
            NotificationUtils.errorNotification("please install FreeLine first");
        }
    }

    /**
     * 设置参数
     * @return
     */
    protected String getArgs() {
        return null;
    }
}
