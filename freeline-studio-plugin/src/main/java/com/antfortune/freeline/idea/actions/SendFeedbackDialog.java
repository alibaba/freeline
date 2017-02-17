package com.antfortune.freeline.idea.actions;

import com.antfortune.freeline.idea.utils.NotificationUtils;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class SendFeedbackDialog extends JDialog implements OnRequestCallback {

    public static final int MAX_WIDTH = 600;
    public static final int MAX_HEIGHT = 450;

    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JTextArea feedbackContentTextArea;

    public SendFeedbackDialog() {
        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(buttonOK);

        setTitle("Freeline Send Feedback");
        setResizable(false);
        setLocationCenter();

        buttonOK.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onOK();
            }
        });

        buttonCancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        });

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });

        contentPane.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    }

    private void onOK() {
        String content = feedbackContentTextArea.getText();
        if (content == null || content.length() == 0) {
            NotificationUtils.errorNotification("Feedback content can not be empty.");
            return;
        }

        ApplicationInfoEx appInfo = ApplicationInfoEx.getInstanceEx();
        boolean eap = appInfo.isEAP();
        String buildInfo = eap?appInfo.getBuild().asStringWithoutProductCode():appInfo.getBuild().asString();
        String timezone = System.getProperty("user.timezone");
        String desc = getDescription();

        SendFeedbackAsync task = new SendFeedbackAsync(content, buildInfo + ";" + timezone + ";" + desc, this);
        ApplicationManager.getApplication().executeOnPooledThread(task);

        buttonOK.setEnabled(false);
    }

    private void onCancel() {
        dispose();
    }

    public void setLocationCenter() {
        int windowWidth = MAX_WIDTH;
        int windowHeight = MAX_HEIGHT;
        Toolkit kit = Toolkit.getDefaultToolkit();
        Dimension screenSize = kit.getScreenSize();
        int screenWidth = screenSize.width;
        int screenHeight = screenSize.height;
        this.setLocation(screenWidth / 2 - windowWidth / 2, screenHeight / 2 - windowHeight / 2);//设置窗口居中显示
    }

    public void showDialog() {
        pack();
        setVisible(true);
    }

    private static String getDescription() {
        StringBuilder sb = new StringBuilder();
        String javaVersion = System.getProperty("java.runtime.version", System.getProperty("java.version", "unknown"));
        sb.append(javaVersion);
        String archDataModel = System.getProperty("sun.arch.data.model");
        if(archDataModel != null) {
            sb.append("x").append(archDataModel);
        }

        String javaVendor = System.getProperty("java.vm.vendor");
        if(javaVendor != null) {
            sb.append(" ").append(javaVendor);
        }

        sb.append(", ").append(System.getProperty("os.name"));
        String osArch = System.getProperty("os.arch");
        if(osArch != null) {
            sb.append("(").append(osArch).append(")");
        }

        String osVersion = System.getProperty("os.version");
        String osPatchLevel = System.getProperty("sun.os.patch.level");
        if(osVersion != null) {
            sb.append(" v").append(osVersion);
            if(osPatchLevel != null) {
                sb.append(" ").append(osPatchLevel);
            }
        }

        if(!GraphicsEnvironment.isHeadless()) {
            GraphicsDevice[] devices = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
            sb.append(" (");

            for(int i = 0; i < devices.length; ++i) {
                if(i > 0) {
                    sb.append(", ");
                }

                GraphicsDevice device = devices[i];
                Rectangle bounds = device.getDefaultConfiguration().getBounds();
                sb.append(bounds.width).append("x").append(bounds.height);
            }

            if(UIUtil.isRetina()) {
                sb.append(" R");
            }

            sb.append(")");
        }

        return sb.toString();
    }

    public static void main(String[] args) {
        SendFeedbackDialog dialog = new SendFeedbackDialog();
        dialog.pack();
        dialog.setVisible(true);
        System.exit(0);
    }

    @Override
    public void onSuccess() {
        NotificationUtils.infoNotification("Submit succeeded, thanks!");
        dispose();
    }

    @Override
    public void onFailure(Exception e) {
        NotificationUtils.errorNotification("Submit failed: " + e.getMessage());
        dispose();
    }

    private static class SendFeedbackAsync implements Runnable {

        private String content;

        private String env;

        private OnRequestCallback callback;

        public SendFeedbackAsync(String content, String env, OnRequestCallback callback) {
            this.content = content;
            this.env = env;
            this.callback = callback;
        }

        @Override
        public void run() {
            try {
                URL url = new URL("https://www.freelinebuild.com/api/feedback");
                //URL url = new URL("http://localhost:3000/api/feedback");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setReadTimeout(10000);
                conn.setConnectTimeout(15000);
                conn.setRequestMethod("POST");
                conn.setDoInput(true);
                conn.setDoOutput(true);

                StringBuilder builder = new StringBuilder();
                builder.append(URLEncoder.encode("content", "UTF-8"));
                builder.append("=");
                builder.append(URLEncoder.encode(content, "UTF-8"));
                builder.append("&");
                builder.append(URLEncoder.encode("env", "UTF-8"));
                builder.append("=");
                builder.append(URLEncoder.encode(env, "UTF-8"));

                OutputStream os = conn.getOutputStream();
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
                writer.write(builder.toString());
                writer.flush();
                writer.close();
                os.close();

                int responseCode = conn.getResponseCode();
                if (responseCode >= 400) {
                    this.callback.onFailure(new Exception(conn.getResponseMessage()));
                } else {
                    this.callback.onSuccess();
                }
                conn.disconnect();
            } catch (IOException e) {
                this.callback.onFailure(e);
            }
        }
    }
}
