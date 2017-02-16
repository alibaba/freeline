package com.antfortune.freeline.idea.views;

import com.antfortune.freeline.idea.utils.Utils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class CheckUpdateDialog extends JDialog {

    public static final int MAX_WIDTH = 600;
    public static final int MAX_HEIGHT = 450;

    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JPanel container;
    private MulLabel serverVersionTx = new MulLabel();
    private MulLabel serverUpdateTimeTx = new MulLabel();
    private MulLabel localVersionTx = new MulLabel();

    public CheckUpdateDialog() {
        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(buttonOK);
        setTitle("Freeline Gradle Plugin Update");
        container.setLayout(null);
        ImageJPanel panel = new ImageJPanel();
        panel.setBounds(0, 0, MAX_WIDTH, 120);
        panel.setImagePath("/icons/bg_update.png");
        container.add(panel);
        JPanel updateContent = new JPanel();
        updateContent.setBounds(20, 130, MAX_WIDTH, 500);
        updateContent.setLayout(new BoxLayout(updateContent, BoxLayout.PAGE_AXIS));
        container.add(updateContent);
        updateContent.add(new JTitle("Jcenter Version"));
        updateContent.add(serverVersionTx);
        updateContent.add(serverUpdateTimeTx);
        updateContent.add(new JTitle("Local Version"));
        updateContent.add(localVersionTx);
        updateContent.add(new JTitle("Version History"));
        MulLabel history = new MulLabel("<a href='#'>github.com/alibaba/freeline/releases</a>", true);
        history.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Utils.openUrl("https://github.com/alibaba/freeline/releases");
            }
        });
        updateContent.add(history);
        updateContent.add(new JTitle("Official Website"));
        MulLabel website = new MulLabel("<a href='#'>github.com/alibaba/freeline</a>", true);
        website.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Utils.openUrl("https://github.com/alibaba/freeline");
            }
        });
        updateContent.add(website);
        updateContent.add(new MulLabel("NOTE: Click update button will sync project automatically and download the latest<br/>freeline.zip Kit", true));
        setResizable(false);
        setLocationCenter();
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

    public void setLocationCenter() {
        int windowWidth = MAX_WIDTH;
        int windowHeight = MAX_HEIGHT;
        Toolkit kit = Toolkit.getDefaultToolkit();
        Dimension screenSize = kit.getScreenSize();
        int screenWidth = screenSize.width;
        int screenHeight = screenSize.height;
        this.setLocation(screenWidth / 2 - windowWidth / 2, screenHeight / 2 - windowHeight / 2);//设置窗口居中显示
    }

    private void onOK() {
        dispose();
    }

    private void onCancel() {
        dispose();
    }

    public void showDialog() {
        pack();
        setVisible(true);
    }

    public void setServerVersion(String version) {
        serverVersionTx.setText(version);
    }

    public void setServerVersion(String groupId, String artifactId, String version) {
        String serverVersionText = groupId + ":" + artifactId + ":" + version;
        if (version.split("\\.").length == 4) {
            serverVersionText += "&nbsp;&nbsp;<font color=red>(Not Release Version)</font>";
        }
        setServerVersion("<html>" + serverVersionText + "</html>");
    }

    public void setServerUpdateTime(String time) {
        serverUpdateTimeTx.setText(time);
    }

    public void setLocalVersion (String localVersion) {
        localVersionTx.setHtml(localVersion);
    }

    public static void main(String[] args) {
        CheckUpdateDialog dialog = new CheckUpdateDialog();
        dialog.showDialog();
    }

    public JButton getButtonOK() {
        return buttonOK;
    }

    public void addActionListener(ActionListener listener) {
        buttonOK.addActionListener(listener);
    }

}
