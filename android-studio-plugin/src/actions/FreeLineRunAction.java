package actions;

import utils.NotificationUtils;
import utils.Utils;
import views.FreelineTerminal;

/**
 * Created by pengwei on 16/9/11.
 */
public class FreeLineRunAction extends BaseAction {
    @Override
    public void actionPerformed() {
        if (checkFreeLineExist()) {
            String python = Utils.getPythonLocation();
            if (python == null) {
                NotificationUtils.errorNotification("command 'python' not found");
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
