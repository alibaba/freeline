package com.antfortune.freeline.idea.utils;

import com.intellij.openapi.util.SystemInfo;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Created by pengwei on 16/9/11.
 */
public final class Utils {

    public static final String BREAK_LINE = System.getProperty("line.separator");

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
        } catch (Exception e) {
        }
        try {
            if (!SystemInfo.isWindows) {
                process = Runtime.getRuntime().exec(new String[]{"whereis", "python"});
                if (process != null && process.getInputStream() != null) {
                    String result = StreamUtil.inputStream2String(process.getInputStream());
                    if (notEmpty(result)) {
                        return result;
                    }
                }
            }
        } catch (IOException e) {
        }
        return null;
    }

    public static boolean notEmpty(String text) {
        return (text != null && text.trim().length() != 0);
    }

    /**
     * 打开浏览器
     *
     * @param url
     */
    public static void openUrl(String url) {
        if (SystemInfo.isWindows) {
            try {
                Runtime.getRuntime().exec("rundll32 url.dll,FileProtocolHandler " + url);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            try {
                URI uri = new URI(url);
                Desktop desktop = null;
                if (Desktop.isDesktopSupported()) {
                    desktop = Desktop.getDesktop();
                }
                if (desktop != null)
                    desktop.browse(uri);
            } catch (IOException ioe) {
                ioe.printStackTrace();
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 模拟键盘输入
     * @param r
     * @param key
     */
    public static void keyPressWithCtrl(Robot r, int key) {
        if (r == null) {
            return;
        }
        r.keyPress(KeyEvent.VK_CONTROL);
        r.keyPress(key);
        r.keyRelease(key);
        r.keyRelease(KeyEvent.VK_CONTROL);
        r.delay(100);
    }

}
