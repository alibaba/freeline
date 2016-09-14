package views;

import javax.swing.*;

/**
 * Created by pengwei on 16/9/14.
 */
public class MyLabel extends JLabel {

    public MyLabel() {
        this("");
    }

    public MyLabel(String text) {
        this(text, false);
    }

    public MyLabel(String text, boolean html) {
        super(text);
        if (html) {
            setText("<html>" + text + "</html>");
        }
        setBorder(BorderFactory.createEmptyBorder(3,0,3,0));
    }

    public void setHtml(String text) {
        setText("<html>" + text + "</html>");
    }
}
