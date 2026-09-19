package com.cognex.insight.ui.panel;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class ImageDisplayPanel extends JPanel {
    private BufferedImage currentImage;
    private double scale = 1.0;
    private int offsetX = 0;
    private int offsetY = 0;

    public ImageDisplayPanel() {
        setBackground(Color.DARK_GRAY);
        setLayout(new BorderLayout());
    }

    public void setImage(BufferedImage image) {
        this.currentImage = image;
        repaint();
    }

    public void clearImage() {
        this.currentImage = null;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (currentImage == null) {
            g.setColor(Color.GRAY);
            String msg = "未连接";
            FontMetrics fm = g.getFontMetrics();
            int x = (getWidth() - fm.stringWidth(msg)) / 2;
            int y = getHeight() / 2;
            g.drawString(msg, x, y);
            return;
        }

        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        int panelW = getWidth();
        int panelH = getHeight();
        int imgW = currentImage.getWidth();
        int imgH = currentImage.getHeight();

        double scaleX = (double) panelW / imgW;
        double scaleY = (double) panelH / imgH;
        scale = Math.min(scaleX, scaleY);

        int drawW = (int) (imgW * scale);
        int drawH = (int) (imgH * scale);
        offsetX = (panelW - drawW) / 2;
        offsetY = (panelH - drawH) / 2;

        g2d.drawImage(currentImage, offsetX, offsetY, drawW, drawH, null);
    }
}
