package actions;

import utils.FreelineUtil;
import utils.NotificationUtils;
import utils.Utils;
import views.FreelineTerminal;

/**
 * Created by pengwei on 16/9/11.
 */
public class FreelineRunAction extends BaseAction {
    @Override
    public void actionPerformed() {
        if (FreelineUtil.checkInstall(currentProject)) {
            String python = Utils.getPythonLocation();
            if (python == null) {
                NotificationUtils.pythonNotFound();
            } else {
                FreelineTerminal.getInstance(currentProject).initAndExecute(new String[]{
                        python, "freeline.py", getArgs()});
            }
        }
    }

    /**
     * 设置参数
     *
     * @return
     */
    protected String getArgs() {
        return null;
    }
}
