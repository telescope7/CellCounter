package com.prolymphname.cellcounter.application;

import com.prolymphname.cellcounter.trackingadapter.TrackStatusSnapshot;
import com.prolymphname.cellcounter.trackingadapter.TrackedCell;
import com.prolymphname.cellcounter.trackingadapter.TrackedCellHistoryEntry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TrackingQualityCalculator {
    private static final double MISSED_FRAME_PENALTY = 0.08;
    private static final double OCCLUSION_PENALTY = 0.18;
    private static final double HIGH_CONFIDENCE_THRESHOLD = 0.75;
    private static final double TRACK_WEIGHT_BASE = 0.25;
    private static final double TRACK_WEIGHT_SCALE = 0.75;

    public TrackingQualitySummary calculate(
            List<TrackedCell> trackedCells,
            List<TrackStatusSnapshot> currentTrackStatuses,
            int frameWidth,
            double fps,
            int confidenceFieldWidthPercent) {
        if (currentTrackStatuses == null || currentTrackStatuses.isEmpty()) {
            return TrackingQualitySummary.empty();
        }

        Map<Integer, TrackedCell> trackedCellById = new HashMap<>();
        if (trackedCells != null) {
            for (TrackedCell trackedCell : trackedCells) {
                trackedCellById.put(trackedCell.cellId(), trackedCell);
            }
        }

        int activeTracks = currentTrackStatuses.size();
        int highConfidenceTracks = 0;
        int watchTracks = 0;
        int occlusionRiskTracks = 0;
        double weightedConfidenceSum = 0.0;
        double totalWeight = 0.0;

        for (TrackStatusSnapshot status : currentTrackStatuses) {
            TrackedCell trackedCell = trackedCellById.get(status.cellId());
            double maturityScore = calculateMaturityScore(trackedCell, fps);
            double widthTraversalScore = calculateWidthTraversalConfidenceScore(
                    trackedCell,
                    frameWidth,
                    confidenceFieldWidthPercent);
            double continuityScore = calculateContinuityScore(status);
            double confidence = calculateTrackConfidence(maturityScore, widthTraversalScore, continuityScore);
            double trackWeight = TRACK_WEIGHT_BASE + (TRACK_WEIGHT_SCALE * Math.max(maturityScore, widthTraversalScore));
            weightedConfidenceSum += confidence * trackWeight;
            totalWeight += trackWeight;

            if (status.occlusionRisk()) {
                occlusionRiskTracks++;
            }
            if (status.watchState()) {
                watchTracks++;
            }
            if (!status.watchState()
                    && confidence >= HIGH_CONFIDENCE_THRESHOLD
                    && widthTraversalScore >= 0.5) {
                highConfidenceTracks++;
            }
        }

        int confidencePercent = clampPercent(Math.round((float) ((weightedConfidenceSum / Math.max(totalWeight, 0.0001)) * 100.0)));
        return new TrackingQualitySummary(
                confidencePercent,
                activeTracks,
                highConfidenceTracks,
                watchTracks,
                occlusionRiskTracks);
    }

    private double calculateTrackConfidence(
            double maturityScore,
            double widthTraversalScore,
            double continuityScore) {
        return clamp01((0.20 * maturityScore) + (0.55 * widthTraversalScore) + (0.25 * continuityScore));
    }

    private double calculateMaturityScore(TrackedCell trackedCell, double fps) {
        int trackedFrames = trackedCell == null ? 0 : trackedCell.history().size();
        double matureFrameThreshold = Math.max(10.0, fps > 0.0 ? fps * 0.50 : 15.0);
        return clamp01(trackedFrames / matureFrameThreshold);
    }

    private double calculateWidthTraversalConfidenceScore(
            TrackedCell trackedCell,
            int frameWidth,
            int confidenceFieldWidthPercent) {
        if (frameWidth <= 0) {
            return 0.0;
        }
        double widthRatio = calculateWidthTraversalRatio(trackedCell, frameWidth);
        double targetRatio = clamp01(confidenceFieldWidthPercent / 100.0);
        if (targetRatio <= 0.0) {
            return 0.0;
        }
        return clamp01(widthRatio / targetRatio);
    }

    private double calculateWidthTraversalRatio(TrackedCell trackedCell, int frameWidth) {
        if (trackedCell == null || frameWidth <= 0 || trackedCell.history().size() < 2) {
            return 0.0;
        }
        List<TrackedCellHistoryEntry> history = trackedCell.history();
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        for (TrackedCellHistoryEntry item : history) {
            double x = item.centroidX();
            if (x < minX) {
                minX = x;
            }
            if (x > maxX) {
                maxX = x;
            }
        }
        double horizontalSpan = Math.max(0.0, maxX - minX);
        return clamp01(horizontalSpan / frameWidth);
    }

    private double calculateContinuityScore(TrackStatusSnapshot status) {
        double score = 1.0 - (status.missedFrames() * MISSED_FRAME_PENALTY);
        if (status.occlusionRisk()) {
            score -= OCCLUSION_PENALTY;
        }
        return clamp01(score);
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private int clampPercent(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
