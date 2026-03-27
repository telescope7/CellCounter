package com.prolymphname.cellcounter.ui;

import javax.swing.JPanel;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;

import static com.prolymphname.cellcounter.ui.CellCounterUiTheme.BG_BOTTOM;
import static com.prolymphname.cellcounter.ui.CellCounterUiTheme.BG_TOP;

public class GradientPanel extends JPanel {
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        GradientPaint bg = new GradientPaint(0, 0, BG_TOP, 0, getHeight(), BG_BOTTOM);
        g2.setPaint(bg);
        g2.fillRect(0, 0, getWidth(), getHeight());

        g2.setColor(new java.awt.Color(62, 127, 255, 52));
        g2.fill(new Ellipse2D.Double(-80, -90, 420, 260));
        g2.setColor(new java.awt.Color(36, 95, 206, 48));
        g2.fill(new Ellipse2D.Double(getWidth() - 320, -70, 420, 300));
        g2.setColor(new java.awt.Color(22, 64, 166, 46));
        g2.fill(new Ellipse2D.Double(getWidth() - 260, getHeight() - 180, 380, 260));
        g2.dispose();
    }
}
