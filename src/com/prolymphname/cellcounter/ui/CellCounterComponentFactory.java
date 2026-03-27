package com.prolymphname.cellcounter.ui;

import javax.swing.AbstractButton;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import static com.prolymphname.cellcounter.ui.CellCounterUiTheme.ACCENT_DEEP;
import static com.prolymphname.cellcounter.ui.CellCounterUiTheme.FONT_BODY;
import static com.prolymphname.cellcounter.ui.CellCounterUiTheme.FONT_BUTTON;
import static com.prolymphname.cellcounter.ui.CellCounterUiTheme.FONT_H2;
import static com.prolymphname.cellcounter.ui.CellCounterUiTheme.FONT_LABEL;
import static com.prolymphname.cellcounter.ui.CellCounterUiTheme.PRIMARY_ACTION;
import static com.prolymphname.cellcounter.ui.CellCounterUiTheme.SPACE_S;
import static com.prolymphname.cellcounter.ui.CellCounterUiTheme.SPACE_XS;
import static com.prolymphname.cellcounter.ui.CellCounterUiTheme.SPACE_XXS;
import static com.prolymphname.cellcounter.ui.CellCounterUiTheme.TEXT_PRIMARY;
import static com.prolymphname.cellcounter.ui.CellCounterUiTheme.TEXT_SECONDARY;

public final class CellCounterComponentFactory {
    private CellCounterComponentFactory() {
    }

    public static CardPanel createCard(String title, String subtitle, boolean showHeading) {
        CardPanel card = new CardPanel();
        card.setLayout(new java.awt.BorderLayout(SPACE_S, SPACE_S));
        card.setBorder(new EmptyBorder(
                CellCounterUiTheme.SPACE_M,
                CellCounterUiTheme.SPACE_M,
                CellCounterUiTheme.SPACE_M,
                CellCounterUiTheme.SPACE_M));
        if (showHeading) {
            javax.swing.JPanel heading = new javax.swing.JPanel();
            heading.setOpaque(false);
            heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));

            JLabel titleLabel = new JLabel(title);
            titleLabel.setFont(FONT_H2);
            titleLabel.setForeground(TEXT_PRIMARY);

            JLabel subtitleLabel = new JLabel(subtitle);
            subtitleLabel.setFont(FONT_BODY);
            subtitleLabel.setForeground(TEXT_SECONDARY);

            heading.add(titleLabel);
            heading.add(Box.createVerticalStrut(SPACE_XXS));
            heading.add(subtitleLabel);

            card.add(heading, java.awt.BorderLayout.NORTH);
        }
        return card;
    }

    public static JButton createPrimaryButton(String text, Icon icon) {
        JButton button = new JButton(text, icon);
        styleButton(button, PRIMARY_ACTION, Color.WHITE, new Color(27, 84, 228));
        return button;
    }

    public static JButton createSecondaryButton(String text, Icon icon) {
        JButton button = new JButton(text, icon);
        styleButton(button, PRIMARY_ACTION, Color.WHITE, new Color(27, 84, 228));
        return button;
    }

    public static JToggleButton createToggleButton(String text, Icon icon) {
        JToggleButton toggle = new JToggleButton(text, icon);
        styleButton(toggle, PRIMARY_ACTION, Color.WHITE, new Color(27, 84, 228));
        toggle.setSelectedIcon(new AppIcon(AppIcon.Kind.EYE, Color.WHITE));
        toggle.addChangeListener(e -> {
            if (toggle.isSelected()) {
                toggle.setBackground(ACCENT_DEEP);
                toggle.setForeground(Color.WHITE);
            } else {
                toggle.setBackground(PRIMARY_ACTION);
                toggle.setForeground(Color.WHITE);
            }
        });
        return toggle;
    }

    public static void configureIconOnlyButton(AbstractButton button, String tooltip) {
        button.setText("");
        button.setToolTipText(tooltip);
        button.setHorizontalAlignment(SwingConstants.CENTER);
        button.setHorizontalTextPosition(SwingConstants.CENTER);
        button.setIconTextGap(0);
        button.setMargin(new Insets(SPACE_XS, SPACE_XS, SPACE_XS, SPACE_XS));
    }

    public static void enforceButtonSize(AbstractButton button, int minWidth) {
        int width = Math.max(minWidth, button.getPreferredSize().width + SPACE_XS);
        int height = Math.max(34, button.getPreferredSize().height + SPACE_XXS);
        Dimension size = new Dimension(width, height);
        button.setPreferredSize(size);
        button.setMinimumSize(size);
    }

    public static JLabel createChipLabel(String text, Color bg) {
        JLabel chip = new JLabel(text, SwingConstants.CENTER);
        chip.setFont(FONT_BUTTON);
        chip.setForeground(Color.WHITE);
        chip.setOpaque(true);
        chip.setBackground(bg);
        chip.setBorder(new EmptyBorder(SPACE_XXS, SPACE_S, SPACE_XXS, SPACE_S));
        return chip;
    }

    public static void styleConfigCheckBox(JCheckBox checkBox) {
        checkBox.setOpaque(false);
        checkBox.setForeground(TEXT_PRIMARY);
        checkBox.setFont(FONT_BODY);
        checkBox.setFocusPainted(false);
    }

    private static void styleButton(AbstractButton button, Color background, Color foreground, Color hoverBackground) {
        button.setFont(FONT_BUTTON);
        button.setBackground(background);
        button.setForeground(foreground);
        button.setFocusPainted(false);
        button.setBorder(new RoundedBorder(new Color(0, 0, 0, 0), 16));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setMargin(new Insets(SPACE_XS, SPACE_S, SPACE_XS, SPACE_S));
        button.setHorizontalTextPosition(SwingConstants.RIGHT);
        button.setIconTextGap(SPACE_XS);
        button.setOpaque(true);

        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent evt) {
                if (button.isEnabled() && !(button instanceof JToggleButton && ((JToggleButton) button).isSelected())) {
                    button.setBackground(hoverBackground);
                }
            }

            @Override
            public void mouseExited(MouseEvent evt) {
                if (button.isEnabled() && !(button instanceof JToggleButton && ((JToggleButton) button).isSelected())) {
                    button.setBackground(background);
                }
            }
        });
    }
}
