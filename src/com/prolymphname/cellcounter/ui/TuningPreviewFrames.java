package com.prolymphname.cellcounter.ui;

import org.opencv.core.Mat;

public record TuningPreviewFrames(Mat rawFrame, Mat foregroundFrame) implements AutoCloseable {
    @Override
    public void close() {
        if (rawFrame != null) {
            rawFrame.release();
        }
        if (foregroundFrame != null) {
            foregroundFrame.release();
        }
    }
}
