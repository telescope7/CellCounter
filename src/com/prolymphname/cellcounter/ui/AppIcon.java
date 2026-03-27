package com.prolymphname.cellcounter.ui;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;

public class AppIcon implements Icon {
    public enum Kind {
        SEARCH,
        BOLT,
        PLAY,
        PAUSE,
        STEP,
        RESET,
        EYE,
        FILE,
        GRID,
        SETTINGS,
        HELP,
        SIMULATOR,
        SLIDERS
    }

    private final Kind kind;
    private final Color color;

    public AppIcon(Kind kind, Color color) {
        this.kind = kind;
        this.color = color;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.translate(x, y);
        g2.setColor(color);
        g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        switch (kind) {
            case SEARCH -> {
                g2.drawOval(1, 1, 8, 8);
                g2.drawLine(8, 8, 13, 13);
            }
            case BOLT -> {
                Path2D bolt = new Path2D.Float();
                bolt.moveTo(7, 0);
                bolt.lineTo(3, 7);
                bolt.lineTo(7, 7);
                bolt.lineTo(5, 14);
                bolt.lineTo(12, 6);
                bolt.lineTo(8, 6);
                bolt.closePath();
                g2.fill(bolt);
            }
            case PLAY -> {
                Path2D tri = new Path2D.Float();
                tri.moveTo(3, 2);
                tri.lineTo(12, 7);
                tri.lineTo(3, 12);
                tri.closePath();
                g2.fill(tri);
            }
            case PAUSE -> {
                g2.fillRoundRect(3, 2, 3, 10, 1, 1);
                g2.fillRoundRect(9, 2, 3, 10, 1, 1);
            }
            case STEP -> {
                Path2D tri = new Path2D.Float();
                tri.moveTo(2, 2);
                tri.lineTo(9, 7);
                tri.lineTo(2, 12);
                tri.closePath();
                g2.fill(tri);
                g2.setStroke(new BasicStroke(2f));
                g2.drawLine(11, 2, 11, 12);
            }
            case RESET -> {
                g2.drawArc(1, 1, 12, 12, 30, 290);
                Path2D arrow = new Path2D.Float();
                arrow.moveTo(11, 0);
                arrow.lineTo(14, 1);
                arrow.lineTo(12, 4);
                arrow.closePath();
                g2.fill(arrow);
            }
            case EYE -> {
                g2.drawOval(1, 4, 13, 7);
                g2.fillOval(6, 6, 3, 3);
            }
            case FILE -> {
                g2.drawRoundRect(2, 1, 10, 12, 2, 2);
                g2.drawLine(4, 5, 10, 5);
                g2.drawLine(4, 8, 10, 8);
                g2.drawLine(4, 11, 8, 11);
            }
            case GRID -> {
                g2.drawRoundRect(1, 1, 12, 12, 2, 2);
                g2.drawLine(5, 1, 5, 13);
                g2.drawLine(9, 1, 9, 13);
                g2.drawLine(1, 5, 13, 5);
                g2.drawLine(1, 9, 13, 9);
            }
            case SETTINGS -> {
                g2.drawOval(2, 2, 10, 10);
                g2.fillOval(5, 5, 4, 4);
                g2.drawLine(7, 0, 7, 2);
                g2.drawLine(7, 12, 7, 14);
                g2.drawLine(0, 7, 2, 7);
                g2.drawLine(12, 7, 14, 7);
            }
            case HELP -> {
                g2.drawOval(1, 1, 12, 12);
                g2.drawArc(4, 3, 6, 5, 0, 200);
                g2.drawLine(7, 8, 7, 9);
                g2.fillOval(6, 11, 2, 2);
            }
            case SIMULATOR -> {
                g2.drawRoundRect(1, 2, 12, 10, 2, 2);
                g2.drawLine(3, 0, 3, 2);
                g2.drawLine(11, 0, 11, 2);
                g2.drawLine(1, 5, 13, 5);
                g2.drawLine(3, 7, 6, 7);
                g2.drawLine(8, 7, 11, 7);
            }
            case SLIDERS -> {
                g2.drawLine(1, 3, 13, 3);
                g2.drawLine(1, 7, 13, 7);
                g2.drawLine(1, 11, 13, 11);
                g2.fillRoundRect(3, 1, 3, 4, 2, 2);
                g2.fillRoundRect(8, 5, 3, 4, 2, 2);
                g2.fillRoundRect(5, 9, 3, 4, 2, 2);
            }
            default -> {
            }
        }

        g2.dispose();
    }

    @Override
    public int getIconWidth() {
        return 14;
    }

    @Override
    public int getIconHeight() {
        return 14;
    }
}
