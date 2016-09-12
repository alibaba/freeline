package views;

import javax.swing.*;
import java.awt.*;

/**
 * Created by pengwei on 16/9/11.
 */
public class GradleInfoPanel extends JPanel {
    private JLabel title;
    private TextArea textArea;

    public GradleInfoPanel(String title, String content) {
        setLayout(null);
        this.title = new JLabel(title);
        this.title.setBounds(5, 10, 600, 20);
        this.title.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        add(this.title);
        this.textArea = new TextArea();
        this.textArea.setRows(4);
        this.textArea.setEditable(false);
        this.textArea.setBounds(5, 35, 600, 100);
        textArea.setText(content);
//        add(textArea);
    }
}
