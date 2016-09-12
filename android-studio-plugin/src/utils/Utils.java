package utils;

import com.github.rjeschke.txtmark.Run;
import com.jediterm.terminal.ui.UIUtil;

import java.io.IOException;

/**
 * Created by pengwei on 16/9/11.
 */
public final class Utils {

    /**
     * 获取python安装目录
     *
     * @return
     */
    public static String getPythonLocation() {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(new String[]{"python", "--version"});
            if (process.waitFor() == 0) {
                return "python";
            }
        } catch (IOException | InterruptedException e) {
        }
        try {
            if (!UIUtil.isWindows) {
                process = Runtime.getRuntime().exec(new String[]{"whereis", "python"});
                if (process != null && process.getInputStream() != null) {
                    String result = StreamUtil.inputStream2String(process.getInputStream());
                    if (result != null && result.trim() != "") {
                        return result;
                    }
                }
            }
        } catch (IOException e) {
        }
        return null;
    }
}
