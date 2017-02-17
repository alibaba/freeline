package com.antfortune.freeline.idea.actions;

import com.antfortune.freeline.idea.icons.PluginIcons;

/**
 * Created by pengwei on 16/9/11.
 */
public class DebugRunAction extends FreelineRunAction {

    public DebugRunAction() {
        super(PluginIcons.StartDebugger);
    }

    @Override
    protected String getArgs() {
        return "-d";
    }
}
