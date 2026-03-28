package com.prolymphname.cellcounter.analysis;

import org.opencv.core.Point;
import org.opencv.core.Rect;

public record DetectionCandidate(
        Rect bbox,
        double contourAreaPx) {

    public Point centroid() {
        return new Point(bbox.x + bbox.width / 2.0, bbox.y + bbox.height / 2.0);
    }
}
