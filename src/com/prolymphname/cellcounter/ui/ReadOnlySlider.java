package com.prolymphname.cellcounter.ui;

import javax.swing.JSlider;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

public class ReadOnlySlider extends JSlider {
    public ReadOnlySlider(int min, int max, int value) {
        super(min, max, value);
        setFocusable(false);
        setRequestFocusEnabled(false);
    }

    @Override
    protected void processMouseEvent(MouseEvent e) {
        // Display only: ignore direct mouse interaction so the frame position
        // remains an indicator rather than a seek control.
    }

    @Override
    protected void processMouseMotionEvent(MouseEvent e) {
        // Display only: ignore drag interaction.
    }

    @Override
    protected void processMouseWheelEvent(MouseWheelEvent e) {
        // Display only: ignore wheel interaction.
    }

    @Override
    protected void processKeyEvent(KeyEvent e) {
        // Display only: ignore keyboard interaction.
    }
}
