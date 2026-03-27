package com.prolymphname.cellcounter.analysis;

import org.opencv.core.Point;
import org.opencv.core.Rect;

public record TrackedOverlay(int cellId, Rect bbox, Point centroid, boolean newTrack) {
}
