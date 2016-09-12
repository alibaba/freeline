package actions;


import com.jediterm.terminal.ui.UIUtil;

/**
 * Created by pengwei on 16/9/12.
 */
public class ZipUpdateAction extends BaseAction {
    @Override
    public void actionPerformed() {
        if (UIUtil.isWindows) {
            executeShell(new String[]{"gradlew.bat", "initFreeline", "-Pmirror"});
        } else {
            executeShell(new String[]{"./gradlew", "initFreeline", "-Pmirror"});
        }
    }
}
