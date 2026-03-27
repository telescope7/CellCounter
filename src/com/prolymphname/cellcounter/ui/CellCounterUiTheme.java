package com.prolymphname.cellcounter.ui;

import java.awt.Color;
import java.awt.Font;

public final class CellCounterUiTheme {
    public static final int SPACE_XXS = 4;
    public static final int SPACE_XS = 8;
    public static final int SPACE_S = 12;
    public static final int SPACE_M = 16;
    public static final int SPACE_L = 24;
    public static final int SPACE_XL = 32;
    public static final int RADIUS_CARD = 22;

    public static final Color BG_TOP = new Color(3, 10, 24);
    public static final Color BG_BOTTOM = new Color(8, 23, 52);
    public static final Color GLASS_SURFACE_TOP = new Color(15, 33, 66, 232);
    public static final Color GLASS_SURFACE_BOTTOM = new Color(10, 24, 50, 222);
    public static final Color BORDER_SOFT = new Color(122, 167, 234, 84);
    public static final Color TEXT_PRIMARY = new Color(235, 245, 255);
    public static final Color TEXT_SECONDARY = new Color(175, 203, 236);
    public static final Color PRIMARY_ACTION = new Color(38, 102, 255);
    public static final Color PRIMARY_ACTION_DARK = new Color(24, 74, 205);
    public static final Color ACCENT = new Color(58, 188, 255);
    public static final Color ACCENT_DEEP = new Color(30, 132, 234);
    public static final Color CHIP_IDLE = new Color(85, 113, 157);
    public static final Color CHIP_ACTIVE = new Color(39, 137, 240);
    public static final Color CHIP_PLAYING = new Color(27, 184, 143);
    public static final Color CHIP_WARNING = new Color(221, 141, 56);

    public static final Font FONT_DISPLAY = resolveFont(
            new String[] { "Avenir Next", "Segoe UI", "Helvetica Neue" },
            Font.BOLD,
            28);
    public static final Font FONT_H2 = resolveFont(
            new String[] { "Avenir Next", "Segoe UI", "Helvetica Neue" },
            Font.BOLD,
            17);
    public static final Font FONT_BODY = resolveFont(
            new String[] { "Avenir Next", "Segoe UI", "Helvetica Neue" },
            Font.PLAIN,
            13);
    public static final Font FONT_LABEL = resolveFont(
            new String[] { "Avenir Next", "Segoe UI", "Helvetica Neue" },
            Font.PLAIN,
            12);
    public static final Font FONT_BUTTON = resolveFont(
            new String[] { "Avenir Next", "Segoe UI", "Helvetica Neue" },
            Font.BOLD,
            12);
    public static final String APP_ICON_FILE_NAME = "cellcounter-icon-1024.png";

    private CellCounterUiTheme() {
    }

    private static Font resolveFont(String[] candidates, int style, int size) {
        for (String family : candidates) {
            Font font = new Font(family, style, size);
            if (!"Dialog".equalsIgnoreCase(font.getFamily())) {
                return font;
            }
        }
        return new Font("SansSerif", style, size);
    }
}
