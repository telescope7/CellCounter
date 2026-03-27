package com.prolymphname.cellcounter.analysis;

import org.opencv.core.Point;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TrackMatchRegionGeometry {
    private static final int DEFAULT_SAMPLE_COUNT = 28;

    public List<Point> buildRegionPolygon(
            Point centroid,
            double minHorizontalMovementPixels,
            double maxVerticalDisplacementPixels,
            double maxAssociationDistancePixels) {
        return buildRegionPolygon(
                centroid,
                minHorizontalMovementPixels,
                maxVerticalDisplacementPixels,
                maxAssociationDistancePixels,
                DEFAULT_SAMPLE_COUNT);
    }

    List<Point> buildRegionPolygon(
            Point centroid,
            double minHorizontalMovementPixels,
            double maxVerticalDisplacementPixels,
            double maxAssociationDistancePixels,
            int sampleCount) {
        if (centroid == null || maxAssociationDistancePixels <= 0.0 || maxVerticalDisplacementPixels < 0.0) {
            return List.of();
        }

        double radius = maxAssociationDistancePixels;
        double startDx = Math.max(-radius, minHorizontalMovementPixels);
        double endDx = radius;
        if (startDx > endDx) {
            return List.of();
        }

        int samples = Math.max(4, sampleCount);
        List<Point> upperBoundary = new ArrayList<>(samples + 1);
        List<Point> lowerBoundary = new ArrayList<>(samples + 1);

        for (int i = 0; i <= samples; i++) {
            double ratio = i / (double) samples;
            double dx = startDx + ((endDx - startDx) * ratio);
            double radialDy = Math.sqrt(Math.max(0.0, (radius * radius) - (dx * dx)));
            double clampedDy = Math.min(maxVerticalDisplacementPixels, radialDy);
            upperBoundary.add(new Point(centroid.x + dx, centroid.y - clampedDy));
            lowerBoundary.add(new Point(centroid.x + dx, centroid.y + clampedDy));
        }

        List<Point> polygon = new ArrayList<>(upperBoundary.size() + lowerBoundary.size());
        polygon.addAll(upperBoundary);
        Collections.reverse(lowerBoundary);
        polygon.addAll(lowerBoundary);
        return polygon;
    }
}
