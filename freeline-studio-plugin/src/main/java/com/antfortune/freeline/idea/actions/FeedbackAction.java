package com.antfortune.freeline.idea.actions;

import com.antfortune.freeline.idea.icons.PluginIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;

/**
 * Created by huangyong on 17/2/14.
 */
public class FeedbackAction extends AnAction {

    public FeedbackAction() {
        super(PluginIcons.EditConfig);
    }

    @Override
    public void actionPerformed(AnActionEvent anActionEvent) {
        SendFeedbackDialog dialog = new SendFeedbackDialog();
        dialog.showDialog();
    }

}
