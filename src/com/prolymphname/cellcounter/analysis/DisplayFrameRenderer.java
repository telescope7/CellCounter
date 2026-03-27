package com.prolymphname.cellcounter.analysis;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

public class DisplayFrameRenderer {
    private static final Scalar NEW_TRACK_COLOR = new Scalar(255, 220, 64);
    private static final Scalar EXISTING_TRACK_COLOR = new Scalar(82, 255, 125);
    private static final Scalar MISSED_TRACK_COLOR = new Scalar(0, 191, 255);
    private static final Scalar OCCLUSION_RISK_COLOR = new Scalar(72, 72, 255);
    private static final Scalar PREVIEW_COLOR = new Scalar(58, 233, 197);
    private static final Scalar LABEL_OUTLINE_COLOR = new Scalar(6, 10, 18);
    private static final double LABEL_FONT_SCALE = 0.375;
    private static final double MATCH_REGION_FILL_ALPHA = 0.18;
    private static final double MATCH_REGION_FILL_SCALE = 0.32;
    private static final double MATCH_REGION_OUTLINE_SCALE = 0.7;
    private final TrackMatchRegionGeometry trackMatchRegionGeometry = new TrackMatchRegionGeometry();

    public Mat renderTrackedFrame(
            Mat sourceFrame,
            boolean showMaskView,
            Mat maskForDisplay,
            List<TrackedOverlay> overlays,
            boolean showTrackTrails,
            boolean showMatchRegion,
            double minHorizontalMovementPixels,
            double maxVerticalDisplacementPixels,
            double maxAssociationDistancePixels) {
        Mat displayOutput;
        if (showMaskView) {
            displayOutput = new Mat();
            Imgproc.cvtColor(maskForDisplay, displayOutput, Imgproc.COLOR_GRAY2BGR);
        } else {
            displayOutput = sourceFrame.clone();
        }

        Mat matchRegionOverlay = null;
        boolean hasMatchRegions = false;
        try {
            if (showMatchRegion && !overlays.isEmpty()) {
                matchRegionOverlay = displayOutput.clone();
                for (TrackedOverlay overlay : overlays) {
                    Scalar regionColor = scaleColor(resolveTrackColor(overlay), MATCH_REGION_FILL_SCALE);
                    if (drawMatchRegion(
                            matchRegionOverlay,
                            displayOutput,
                            overlay,
                            regionColor,
                            minHorizontalMovementPixels,
                            maxVerticalDisplacementPixels,
                            maxAssociationDistancePixels)) {
                        hasMatchRegions = true;
                    }
                }
            }

            if (hasMatchRegions) {
                Core.addWeighted(matchRegionOverlay, MATCH_REGION_FILL_ALPHA, displayOutput,
                        1.0 - MATCH_REGION_FILL_ALPHA, 0.0, displayOutput);
            }

            for (TrackedOverlay overlay : overlays) {
                Scalar color = resolveTrackColor(overlay);
                Rect bbox = overlay.bbox();
                Point centroid = overlay.centroid();

                if (showTrackTrails) {
                    drawTrail(displayOutput, overlay.trailPoints(), color);
                }
                if (bbox != null) {
                    if (overlay.uncertain()) {
                        drawDashedRectangle(displayOutput, bbox, color, 1);
                    } else {
                        Imgproc.rectangle(displayOutput, bbox.tl(), bbox.br(), color, 1);
                    }
                    if (overlay.occlusionRisk()) {
                        Imgproc.rectangle(displayOutput, bbox.tl(), bbox.br(), OCCLUSION_RISK_COLOR, 2);
                    }
                }
                if (centroid != null) {
                    drawTrackLabel(displayOutput, overlay, color);
                    Imgproc.circle(displayOutput, centroid, 4, color, 1);
                    if (overlay.occlusionRisk()) {
                        Imgproc.circle(displayOutput, centroid, 10, OCCLUSION_RISK_COLOR, 2, Imgproc.LINE_AA, 0);
                    }
                }
            }
        } finally {
            if (matchRegionOverlay != null) {
                matchRegionOverlay.release();
            }
        }
        return displayOutput;
    }

    public Mat renderPreviewFrame(Mat sourceFrame, Mat maskForDisplay, List<Rect> rects, boolean showMaskView) {
        Mat display;
        if (showMaskView) {
            display = new Mat();
            Imgproc.cvtColor(maskForDisplay, display, Imgproc.COLOR_GRAY2BGR);
        } else {
            display = sourceFrame.clone();
        }

        for (Rect rect : rects) {
            Imgproc.rectangle(display, rect.tl(), rect.br(), PREVIEW_COLOR, 1);
            Point centroid = new Point(rect.x + rect.width / 2.0, rect.y + rect.height / 2.0);
            Imgproc.circle(display, centroid, 2, PREVIEW_COLOR, 1);
        }

        Imgproc.putText(display, "Preview detections: " + rects.size(),
                new Point(12, 24),
                Imgproc.FONT_HERSHEY_SIMPLEX,
                0.6,
                PREVIEW_COLOR,
                2);
        return display;
    }

    private Scalar resolveTrackColor(TrackedOverlay overlay) {
        if (overlay.occlusionRisk()) {
            return OCCLUSION_RISK_COLOR;
        }
        return switch (overlay.state()) {
            case NEW -> NEW_TRACK_COLOR;
            case MISSED -> MISSED_TRACK_COLOR;
            case STABLE -> EXISTING_TRACK_COLOR;
        };
    }

    private void drawTrail(Mat displayOutput, List<Point> trailPoints, Scalar baseColor) {
        if (trailPoints == null || trailPoints.size() < 2) {
            return;
        }
        for (int i = 1; i < trailPoints.size(); i++) {
            double factor = 0.16 + (0.72 * i / Math.max(1.0, trailPoints.size() - 1.0));
            Scalar segmentColor = scaleColor(baseColor, factor);
            Imgproc.line(displayOutput, trailPoints.get(i - 1), trailPoints.get(i), segmentColor, 1, Imgproc.LINE_AA, 0);
            Imgproc.circle(displayOutput, trailPoints.get(i), 1, segmentColor, 1, Imgproc.LINE_AA, 0);
        }
    }

    private Scalar scaleColor(Scalar color, double factor) {
        return new Scalar(
                clampColor(color.val[0] * factor),
                clampColor(color.val[1] * factor),
                clampColor(color.val[2] * factor));
    }

    private double clampColor(double value) {
        return Math.max(0.0, Math.min(255.0, value));
    }

    private void drawDashedRectangle(Mat displayOutput, Rect bbox, Scalar color, int thickness) {
        Point topLeft = bbox.tl();
        Point topRight = new Point(bbox.x + bbox.width, bbox.y);
        Point bottomLeft = new Point(bbox.x, bbox.y + bbox.height);
        Point bottomRight = bbox.br();
        drawDashedLine(displayOutput, topLeft, topRight, color, thickness);
        drawDashedLine(displayOutput, topRight, bottomRight, color, thickness);
        drawDashedLine(displayOutput, bottomRight, bottomLeft, color, thickness);
        drawDashedLine(displayOutput, bottomLeft, topLeft, color, thickness);
    }

    private void drawDashedLine(Mat displayOutput, Point start, Point end, Scalar color, int thickness) {
        double length = Math.hypot(end.x - start.x, end.y - start.y);
        if (length <= 0.0) {
            return;
        }
        double dashLength = 6.0;
        double gapLength = 4.0;
        for (double position = 0.0; position < length; position += dashLength + gapLength) {
            double dashEnd = Math.min(length, position + dashLength);
            Point dashStartPoint = interpolate(start, end, position / length);
            Point dashEndPoint = interpolate(start, end, dashEnd / length);
            Imgproc.line(displayOutput, dashStartPoint, dashEndPoint, color, thickness, Imgproc.LINE_AA, 0);
        }
    }

    private Point interpolate(Point start, Point end, double ratio) {
        return new Point(
                start.x + ((end.x - start.x) * ratio),
                start.y + ((end.y - start.y) * ratio));
    }

    private void drawTrackLabel(Mat displayOutput, TrackedOverlay overlay, Scalar color) {
        Point centroid = overlay.centroid();
        String label = overlay.labelText();
        int[] baseline = new int[1];
        org.opencv.core.Size labelSize = Imgproc.getTextSize(
                label,
                Imgproc.FONT_HERSHEY_SIMPLEX,
                LABEL_FONT_SCALE,
                1,
                baseline);

        double originX = Math.max(4.0, centroid.x - 14.0);
        double originY = Math.max(labelSize.height + 8.0, centroid.y - 10.0);
        originX = Math.min(originX, Math.max(4.0, displayOutput.cols() - labelSize.width - 12.0));
        originY = Math.min(originY, Math.max(labelSize.height + 8.0, displayOutput.rows() - 6.0));

        Point textOrigin = new Point(originX, originY);
        Imgproc.putText(
                displayOutput,
                label,
                textOrigin,
                Imgproc.FONT_HERSHEY_SIMPLEX,
                LABEL_FONT_SCALE,
                LABEL_OUTLINE_COLOR,
                3,
                Imgproc.LINE_AA,
                false);
        Imgproc.putText(
                displayOutput,
                label,
                textOrigin,
                Imgproc.FONT_HERSHEY_SIMPLEX,
                LABEL_FONT_SCALE,
                color,
                1,
                Imgproc.LINE_AA,
                false);
    }

    private boolean drawMatchRegion(
            Mat fillTarget,
            Mat outlineTarget,
            TrackedOverlay overlay,
            Scalar fillColor,
            double minHorizontalMovementPixels,
            double maxVerticalDisplacementPixels,
            double maxAssociationDistancePixels) {
        if (overlay.centroid() == null) {
            return false;
        }

        List<Point> regionPoints = trackMatchRegionGeometry.buildRegionPolygon(
                overlay.centroid(),
                minHorizontalMovementPixels,
                maxVerticalDisplacementPixels,
                maxAssociationDistancePixels);
        if (regionPoints.size() < 3) {
            return false;
        }

        List<MatOfPoint> polygonList = new ArrayList<>(1);
        MatOfPoint polygon = new MatOfPoint();
        polygon.fromList(regionPoints);
        polygonList.add(polygon);
        try {
            Imgproc.fillPoly(fillTarget, polygonList, fillColor, Imgproc.LINE_AA, 0, new Point(0, 0));
            drawRegionGuides(outlineTarget, overlay.centroid(), regionPoints, overlay.occlusionRisk()
                    ? OCCLUSION_RISK_COLOR
                    : scaleColor(fillColor, MATCH_REGION_OUTLINE_SCALE), minHorizontalMovementPixels);
            return true;
        } finally {
            polygon.release();
        }
    }

    private void drawRegionGuides(
            Mat displayOutput,
            Point centroid,
            List<Point> regionPoints,
            Scalar outlineColor,
            double minHorizontalMovementPixels) {
        for (int i = 1; i < regionPoints.size(); i++) {
            Imgproc.line(displayOutput, regionPoints.get(i - 1), regionPoints.get(i), outlineColor, 1, Imgproc.LINE_AA, 0);
        }
        Imgproc.line(displayOutput, regionPoints.get(regionPoints.size() - 1), regionPoints.get(0), outlineColor, 1, Imgproc.LINE_AA, 0);

        double minDxForGuide = Math.max(0.0, minHorizontalMovementPixels);
        Point regionStart = new Point(centroid.x + minDxForGuide, centroid.y);
        Point regionEnd = new Point(centroid.x + regionPoints.stream().mapToDouble(point -> point.x).max().orElse(centroid.x), centroid.y);
        Imgproc.line(displayOutput, regionStart, regionEnd, outlineColor, 1, Imgproc.LINE_AA, 0);

        double leftBoundaryX = regionPoints.stream().mapToDouble(point -> point.x).min().orElse(centroid.x);
        double topBoundaryY = regionPoints.stream().mapToDouble(point -> point.y).min().orElse(centroid.y);
        double bottomBoundaryY = regionPoints.stream().mapToDouble(point -> point.y).max().orElse(centroid.y);
        Imgproc.line(displayOutput,
                new Point(leftBoundaryX, topBoundaryY),
                new Point(leftBoundaryX, bottomBoundaryY),
                outlineColor,
                1,
                Imgproc.LINE_AA,
                0);
    }
}
