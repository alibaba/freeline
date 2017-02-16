package com.antfortune.freeline.idea.views;

import javax.swing.*;

/**
 * Created by pengwei on 16/9/14.
 */
public class MulLabel extends JLabel {

    public MulLabel() {
        this("");
    }

    public MulLabel(String text) {
        this(text, false);
    }

    public MulLabel(String text, boolean html) {
        super(text);
        if (html) {
            setHtml(text);
        }
        setBorder(BorderFactory.createEmptyBorder(3,0,3,0));
    }

    public void setHtml(String text) {
        setText("<html>" + text + "</html>");
    }
}
