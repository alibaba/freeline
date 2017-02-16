package com.antfortune.freeline.idea.actions;

import com.antfortune.freeline.idea.icons.PluginIcons;

/**
 * Created by pengwei on 16/9/11.
 */
public class ForceRunAction extends FreelineRunAction {

    public ForceRunAction() {
        super(PluginIcons.QuickfixBulb);
    }

    @Override
    protected String getArgs() {
        return "-f";
    }
}
