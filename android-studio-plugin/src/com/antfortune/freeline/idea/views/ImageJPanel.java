package com.antfortune.freeline.idea.views;


import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

/**
 * Created by pengwei on 16/9/14.
 */
public class ImageJPanel extends JPanel {

    private BufferedImage image;

    public void setImagePath(String path) {
        try {
            InputStream is = this.getClass().getClassLoader().getResourceAsStream(path);
            if (is != null) {
                image = ImageIO.read(is);
            }
        } catch (IOException e) {

        }
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);
        g.drawImage(image, 0, 0, getWidth(), getHeight(), null, null);
    }
}
