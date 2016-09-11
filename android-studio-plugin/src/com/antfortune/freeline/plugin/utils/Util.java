package com.antfortune.freeline.plugin.utils;

import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

/**
 * Utility
 *
 * @author act262@gmail.com
 */
public class Util {

    /**
     * 执行指定命令，是否开启新的线程执行异步操作
     */
    public static void execCmd(final ArrayList<String> commands, final File dir, boolean async) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                execCmd(commands, dir);
            }
        };

        // 新开一个线程异步执行
        if (async) {
            new Thread(runnable).start();
        } else {
            runnable.run();
        }
    }

    public static void execCmd(ArrayList<String> commands, File dir) {
        if (SystemUtil.isWindows()) {
            execCmd(wrapWindowsCmds(commands), dir);
        } else {
            execCmd(wrapUnixCmds(commands), dir);
        }
    }

    /**
     * 执行命令行
     */
    private static void execCmd(String[] commands, File dir) {
        try {
            Process p = Runtime.getRuntime().exec(commands, null, dir);
            InputStream ins = p.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int l;
            while ((l = ins.read(buf)) != -1) {
                bos.write(buf, 0, l);
            }
            ins.close();
            if (bos.size() > 0) {
                bos.write('\n');
            }
            ins = p.getErrorStream();
            while ((l = ins.read(buf)) != -1) {
                bos.write(buf, 0, l);
            }
            ins.close();
            int exit = p.waitFor();
            String output = new String(bos.toByteArray());

            // TODO: 16-9-5 提示
            System.out.println("output = " + output);

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    @NotNull
    private static String[] wrapUnixCmds(ArrayList<String> cmds) {
        cmds.add(0, "/bin/sh");
        cmds.add(1, "-c");
        return cmds.toArray(new String[0]);
    }

    @NotNull
    private static String[] wrapWindowsCmds(ArrayList<String> cmds) {
        cmds.add(0, "cmd");
        cmds.add(1, "/c");
        return cmds.toArray(new String[0]);
    }
}
