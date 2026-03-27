package com.prolymphname.cellcounter.ui;

import javax.swing.border.LineBorder;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

import static com.prolymphname.cellcounter.ui.CellCounterUiTheme.SPACE_S;
import static com.prolymphname.cellcounter.ui.CellCounterUiTheme.SPACE_XXS;

public class RoundedBorder extends LineBorder {
    private final int radius;

    public RoundedBorder(Color color, int radius) {
        super(color, 1, true);
        this.radius = radius;
    }

    @Override
    public Insets getBorderInsets(Component c) {
        return new Insets(SPACE_XXS, SPACE_S, SPACE_XXS, SPACE_S);
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(lineColor);
        g2.draw(new RoundRectangle2D.Float(x, y, width - 1, height - 1, radius, radius));
        g2.dispose();
    }
}
