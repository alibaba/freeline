package com.antfortune.freeline.idea.actions;

import com.antfortune.freeline.idea.icons.PluginIcons;
import com.antfortune.freeline.idea.utils.FreelineUtil;
import com.antfortune.freeline.idea.utils.NotificationUtils;
import com.antfortune.freeline.idea.utils.Utils;
import com.antfortune.freeline.idea.views.FreelineTerminal;

import javax.swing.*;

/**
 * Created by pengwei on 16/9/11.
 */
public class FreelineRunAction extends BaseAction {

    public FreelineRunAction() {
        super(PluginIcons.FreelineIcon);
    }

    public FreelineRunAction(Icon icon) {
        super(icon);
    }

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
