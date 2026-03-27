package com.prolymphname.cellcounter.ui;

import javax.swing.JWindow;
import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;

public class StartupSplashWindow extends JWindow {
    private static final int WIDTH = 760;
    private static final int HEIGHT = 360;
    private static final Color SPLASH_TOP = new Color(3, 10, 24);
    private static final Color SPLASH_BOTTOM = new Color(8, 23, 52);
    private static final Color SPLASH_BORDER = new Color(122, 167, 234, 120);

    private final Timer closeTimer;
    private final Runnable onComplete;

    public StartupSplashWindow(int durationMillis, Runnable onComplete) {
        this.onComplete = onComplete;
        setBackground(new Color(0, 0, 0, 0));
        setSize(WIDTH, HEIGHT);
        setAlwaysOnTop(true);
        setLocationRelativeTo(null);
        setContentPane(new SplashPanel());

        closeTimer = new Timer(durationMillis, e -> {
            dispose();
            if (this.onComplete != null) {
                this.onComplete.run();
            }
        });
        closeTimer.setRepeats(false);
    }

    public void showSplash() {
        setVisible(true);
        closeTimer.start();
    }

    private static class SplashPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            GradientPaint bg = new GradientPaint(0, 0, SPLASH_TOP, 0, getHeight(), SPLASH_BOTTOM);
            g2.setPaint(bg);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 28, 28);

            g2.setColor(SPLASH_BORDER);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 28, 28);

            int haloSize = 330;
            g2.setColor(new Color(52, 134, 255, 78));
            g2.fill(new Ellipse2D.Float(-90, -85, haloSize, haloSize * 0.7f));
            g2.setColor(new Color(34, 107, 222, 62));
            g2.fill(new Ellipse2D.Float(getWidth() - 270, -70, 350, 250));

            String title = "Biomaterials Cell Counter";
            String subtitle = "Initializing vision pipeline...";

            FontMetrics fm = g2.getFontMetrics(CellCounterUiTheme.FONT_DISPLAY.deriveFont(54f));
            int baseX = (getWidth() - fm.stringWidth(title)) / 2;
            int baseY = getHeight() / 2 - 8;

            g2.setFont(CellCounterUiTheme.FONT_DISPLAY.deriveFont(54f));
            g2.setColor(new Color(196, 229, 255, 88));
            g2.drawString(title, baseX + 2, baseY + 2);
            g2.setColor(new Color(236, 247, 255));
            g2.drawString(title, baseX, baseY);

            g2.setFont(CellCounterUiTheme.FONT_BODY.deriveFont(17f));
            g2.setColor(new Color(178, 208, 242));
            FontMetrics subMetrics = g2.getFontMetrics();
            int subX = (getWidth() - subMetrics.stringWidth(subtitle)) / 2;
            g2.drawString(subtitle, subX, baseY + 42);

            g2.dispose();
        }
    }
}
