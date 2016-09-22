package icons;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

/**
 * Created by pengwei on 16/9/15.
 */
public class PluginIcons {

    public static final Icon FreelineIcon = load("/icons/icon.png");
    public static final Icon OpenTerminal = load("/icons/OpenTerminal.png");
    public static final Icon Execute = intellijLoad("/actions/execute.png");
    public static final Icon Suspend = intellijLoad("/actions/suspend.png");
    public static final Icon StartDebugger = intellijLoad("/actions/startDebugger.png");
    public static final Icon QuickfixBulb = intellijLoad("/actions/quickfixBulb.png");
    public static final Icon GC = intellijLoad("/actions/gc.png");
    public static final Icon GradleSync = load("/icons/gradlesync.png");

    /* Run action icon */
    public static final Icon ICON_ACTION_RUN = FreelineIcon;
    /* Tool window icon */
    public static final Icon ICON_TOOL_WINDOW = OpenTerminal;

    private static Icon load(String path) {
        return IconLoader.getIcon(path, PluginIcons.class);
    }

    private static Icon androidLoad(String path) {
        return IconLoader.getIcon(path, AndroidIcons.class);
    }

    private static Icon intellijLoad(String path) {
        return IconLoader.getIcon(path, AllIcons.class);
    }
}
