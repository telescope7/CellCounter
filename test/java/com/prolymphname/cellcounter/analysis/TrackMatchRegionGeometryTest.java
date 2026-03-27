package com.prolymphname.cellcounter.analysis;

import org.junit.Test;
import org.opencv.core.Point;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TrackMatchRegionGeometryTest {
    private final TrackMatchRegionGeometry geometry = new TrackMatchRegionGeometry();

    @Test
    public void buildRegionPolygon_returnsEmptyWhenRadiusInvalid() {
        List<Point> points = geometry.buildRegionPolygon(new Point(20, 20), -3.0, 40.0, 0.0);

        assertTrue(points.isEmpty());
    }

    @Test
    public void buildRegionPolygon_respectsVerticalConstraintAndRadius() {
        Point centroid = new Point(100, 100);
        double maxVertical = 20.0;
        double radius = 50.0;
        List<Point> points = geometry.buildRegionPolygon(centroid, 0.0, maxVertical, radius);

        assertFalse(points.isEmpty());
        for (Point point : points) {
            assertTrue(Math.abs(point.y - centroid.y) <= maxVertical + 0.001);
            double dx = point.x - centroid.x;
            double dy = point.y - centroid.y;
            assertTrue((dx * dx) + (dy * dy) <= (radius * radius) + 0.01);
        }
    }

    @Test
    public void buildRegionPolygon_respectsMinimumHorizontalMovement() {
        Point centroid = new Point(80, 60);
        double minHorizontal = 10.0;
        List<Point> points = geometry.buildRegionPolygon(centroid, minHorizontal, 25.0, 45.0);

        assertFalse(points.isEmpty());
        for (Point point : points) {
            assertTrue(point.x >= centroid.x + minHorizontal - 0.001);
        }
    }
}
