package views;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;
import java.awt.*;

/**
 * Created by pengwei on 16/9/14.
 */
public class ImageJPanel extends JPanel {

    private ImageIcon icon;

    public void setImagePath(String path) {
        icon = new ImageIcon(path);
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);
        g.drawImage(icon.getImage(), 0, 0, getWidth(), getHeight(), null, null);
    }
}
