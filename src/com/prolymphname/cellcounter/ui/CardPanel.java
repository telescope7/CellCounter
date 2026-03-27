package com.prolymphname.cellcounter.ui;

import javax.swing.JPanel;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

import static com.prolymphname.cellcounter.ui.CellCounterUiTheme.BORDER_SOFT;
import static com.prolymphname.cellcounter.ui.CellCounterUiTheme.GLASS_SURFACE_BOTTOM;
import static com.prolymphname.cellcounter.ui.CellCounterUiTheme.GLASS_SURFACE_TOP;
import static com.prolymphname.cellcounter.ui.CellCounterUiTheme.RADIUS_CARD;

public class CardPanel extends JPanel {
    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        RoundRectangle2D.Float shape = new RoundRectangle2D.Float(
                0,
                0,
                getWidth() - 1,
                getHeight() - 1,
                RADIUS_CARD,
                RADIUS_CARD);
        GradientPaint gp = new GradientPaint(0, 0, GLASS_SURFACE_TOP, 0, getHeight(), GLASS_SURFACE_BOTTOM);
        g2.setPaint(gp);
        g2.fill(shape);

        g2.setColor(BORDER_SOFT);
        g2.draw(shape);

        g2.setColor(new java.awt.Color(152, 188, 244, 82));
        g2.draw(new RoundRectangle2D.Float(
                1,
                1,
                getWidth() - 3,
                getHeight() - 3,
                RADIUS_CARD - 2,
                RADIUS_CARD - 2));
        g2.dispose();

        super.paintComponent(g);
    }

    @Override
    public boolean isOpaque() {
        return false;
    }
}
