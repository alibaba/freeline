package com.antfortune.freeline.idea.views;

import javax.swing.*;
import java.awt.*;

/**
 * Created by pengwei on 16/9/14.
 */
public class JTitle extends JLabel {

    public JTitle(String text) {
        super(text);
        setFont(new Font("微软雅黑", Font.BOLD, 15));
        setBorder(BorderFactory.createEmptyBorder(10,0,0,0));
    }
}
