package views;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class UpdateDialog extends JDialog implements ActionListener {
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JLabel lastVersion;
    private JScrollPane jScrollPane;
    private JTextArea textArea1;

    public UpdateDialog() {
        setTitle("Freeline gradle plugin update");
        setContentPane(contentPane);
        setModal(true);
        lastVersion.setFont(new Font("微软雅黑", Font.BOLD, 14));
        getRootPane().setDefaultButton(buttonOK);
        setResizable(false);
        buttonCancel.addActionListener(this);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });
        jScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        jScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        textArea1.setLineWrap(true);
        textArea1.setEditable(false);
        contentPane.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    }

    private void onOK() {
        dispose();
    }

    private void onCancel() {
        dispose();
    }

    /**
     * 设置服务器版本
     *
     * @param version
     */
    public void setServerVersion(String version) {
        lastVersion.setText("GitHub Version:  " + version);
    }

    /**
     * 增加内容
     *
     * @param filePath
     * @param content
     */
    public void addGradleContent(String filePath, String content) {
        String[] path = filePath.split("\\/");
        if (path.length >= 3) {
            filePath = path[path.length - 2] + "/" + path[path.length - 1];
        }
        textArea1.append("【" + filePath + "】\n");
        textArea1.append(content);
        textArea1.append("\n\n");
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource().equals(buttonCancel)) {
            onCancel();
        }
    }

    public void setOkActionListener(ActionListener actionListener) {
        if (actionListener != null) {
            buttonOK.addActionListener(actionListener);
        }
    }
}
