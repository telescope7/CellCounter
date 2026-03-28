package com.prolymphname.cellcounter.analysis;

import org.opencv.core.Point;
import org.opencv.core.Rect;

import java.util.ArrayList;
import java.util.List;

public record TrackedOverlay(
        int cellId,
        Rect bbox,
        double contourAreaPx,
        Point centroid,
        TrackVisualState state,
        int missedFrames,
        boolean occlusionRisk,
        List<Point> trailPoints) {

    public TrackedOverlay {
        trailPoints = trailPoints == null ? List.of() : List.copyOf(new ArrayList<>(trailPoints));
    }

    public boolean uncertain() {
        return state == TrackVisualState.MISSED || occlusionRisk;
    }

    public String labelText() {
        return labelText(false);
    }

    public String labelText(boolean includeSizeMetrics) {
        List<String> tags = new ArrayList<>();
        if (includeSizeMetrics) {
            if (contourAreaPx > 0.0) {
                tags.add("A=" + Math.round(contourAreaPx) + " px^2");
            } else if (bbox != null && bbox.width > 0 && bbox.height > 0) {
                tags.add(bbox.width + "x" + bbox.height + " px");
            }
        }
        if (occlusionRisk) {
            tags.add("OCC");
        }
        if (state == TrackVisualState.MISSED) {
            tags.add("MISS " + missedFrames);
        } else if (state == TrackVisualState.NEW) {
            tags.add("NEW");
        }
        if (tags.isEmpty()) {
            return "ID " + cellId;
        }
        return "ID " + cellId + " " + String.join(" ", tags);
    }
}
