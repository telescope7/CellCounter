package com.prolymphname.cellcounter.analysis;

import org.opencv.core.Mat;
import org.opencv.core.Rect;

import java.util.ArrayList;
import java.util.List;

public record DetectionFrameResult(Mat mask, List<DetectionCandidate> detections) implements AutoCloseable {
    public DetectionFrameResult {
        detections = List.copyOf(detections);
    }

    public List<Rect> rects() {
        List<Rect> rects = new ArrayList<>(detections.size());
        for (DetectionCandidate detection : detections) {
            rects.add(detection.bbox());
        }
        return List.copyOf(rects);
    }

    @Override
    public void close() {
        if (mask != null) {
            mask.release();
        }
    }
}
