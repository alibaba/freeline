import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.awt.RelativePoint;

import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;

/**
 * copy from https://github.com/mmin18/LayoutCast
 * The thread from CastAction.java has been separated into this modificated Runnable implementation.
 * <p/>
 * Created by 3mill on 2015-08-19.
 */
public class ActionRunnabe implements Runnable {

    public static final String[] CMDS = {"python", "py", "python.exe"};
    private static Process running;
    private static long runTime;

    private File dir;
    private File castPy;
    private AnActionEvent event;

    public ActionRunnabe(File dir, File castPy, AnActionEvent e) {
        this.dir = dir;
        this.castPy = castPy;
        this.event = e;
    }

    @Override
    public void run() {
        if (running != null && System.currentTimeMillis() - runTime < 5000) {
            return;
        }

        String pythonCommand = null;
        for (String cmd : CMDS) {
            try {
                Process p = Runtime.getRuntime().exec(new String[]{cmd, "--version"});
                if (p.waitFor() == 0) {
                    pythonCommand = cmd;
                    break;
                }
            } catch (Exception e) {
            }
        }
        if (pythonCommand == null) {
            popupBollon(-2, "Program \"python\" is not found in PATH");
//            StatUtils.send(castPy, -2, 0);
            return;
        }

        try {
            if (running != null) {
                running.destroy();
            }
            File androidSdk = getAndroidSdk();
            ArrayList<String> args = new ArrayList<String>();
            args.add(pythonCommand);
            args.add(castPy.getAbsolutePath());
//            if (androidSdk != null) {
//                args.add("--sdk");
//                args.add(androidSdk.getAbsolutePath());
//            }
//            args.add(dir.getAbsolutePath());
            Process p = Runtime.getRuntime().exec(args.toArray(new String[0]), null, dir);
            running = p;
            runTime = System.currentTimeMillis();
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
            popupBollon(exit, output);
//            StatUtils.send(castPy, exit, System.currentTimeMillis() - runTime);
        } catch (Exception e) {
            popupBollon(-1, e.toString());
//            StatUtils.send(castPy, -1, System.currentTimeMillis() - runTime);
        } finally {
            running = null;
        }
    }

    private void popupBollon(final int exit, final String output) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
                String msg = output;
                if (exit != 0 && output.length() > 1512) {
                    try {
                        File tmp = File.createTempFile("lcast_log", ".txt");
                        FileOutputStream fos = new FileOutputStream(tmp);
                        fos.write(output.getBytes());
                        fos.close();

                        msg = output.substring(0, 1500) + "...";
                        msg += "\n<a href=\"file://" + tmp.getAbsolutePath() + "\">see log</a>";
                    } catch (Exception e) {
                    }
                }

                StatusBar statusBar = WindowManager.getInstance()
                        .getStatusBar(DataKeys.PROJECT.getData(event.getDataContext()));
                JBPopupFactory.getInstance()
                        .createHtmlTextBalloonBuilder(msg, exit == 0 ? MessageType.INFO : MessageType.ERROR, new HyperlinkListener() {
                            @Override
                            public void hyperlinkUpdate(HyperlinkEvent e) {
                                if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                                    try {
                                        java.awt.Desktop.getDesktop().browse(e.getURL().toURI());
                                    } catch (Exception ex) {
                                    }
                                }
                            }
                        })
                        .setFadeoutTime(exit == 0 ? 1500 : 6000)
                        .createBalloon()
                        .show(RelativePoint.getCenterOf(statusBar.getComponent()),
                                Balloon.Position.atRight);
            }
        });
    }

    private static File getAndroidSdk() {
        try {
            ClassLoader cl = PluginManager.getPlugin(PluginId.getId("org.jetbrains.android")).getPluginClassLoader();
            Class c = cl.loadClass("com.android.tools.idea.sdk.DefaultSdks");
            return (File) c.getMethod("getDefaultAndroidHome").invoke(null);
        } catch (Exception ex) {
        }
        return null;
    }

}
