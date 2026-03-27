package com.prolymphname.cellcounter.analysis;

import org.opencv.core.Mat;
import org.opencv.core.Rect;

import java.util.List;

public record DetectionFrameResult(Mat mask, List<Rect> rects) implements AutoCloseable {
    public DetectionFrameResult {
        rects = List.copyOf(rects);
    }

    @Override
    public void close() {
        if (mask != null) {
            mask.release();
        }
    }
}
