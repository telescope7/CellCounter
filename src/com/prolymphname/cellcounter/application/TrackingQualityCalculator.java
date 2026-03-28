package com.prolymphname.cellcounter.application;

import com.prolymphname.cellcounter.trackingadapter.TrackStatusSnapshot;
import com.prolymphname.cellcounter.trackingadapter.TrackedCell;
import com.prolymphname.cellcounter.trackingadapter.TrackedCellHistoryEntry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TrackingQualityCalculator {
    private static final double MISSED_FRAME_PENALTY = 0.12;
    private static final double OCCLUSION_PENALTY = 0.22;
    private static final double HIGH_CONFIDENCE_THRESHOLD = 0.75;

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
        double totalConfidence = 0.0;

        for (TrackStatusSnapshot status : currentTrackStatuses) {
            TrackedCell trackedCell = trackedCellById.get(status.cellId());
            double confidence = calculateTrackConfidence(
                    trackedCell,
                    status,
                    frameWidth,
                    fps,
                    confidenceFieldWidthPercent);
            totalConfidence += confidence;

            if (status.occlusionRisk()) {
                occlusionRiskTracks++;
            }
            if (status.watchState()) {
                watchTracks++;
            }
            if (!status.watchState() && confidence >= HIGH_CONFIDENCE_THRESHOLD) {
                highConfidenceTracks++;
            }
        }

        int confidencePercent = clampPercent(Math.round((float) ((totalConfidence / activeTracks) * 100.0)));
        return new TrackingQualitySummary(
                confidencePercent,
                activeTracks,
                highConfidenceTracks,
                watchTracks,
                occlusionRiskTracks);
    }

    private double calculateTrackConfidence(
            TrackedCell trackedCell,
            TrackStatusSnapshot status,
            int frameWidth,
            double fps,
            int confidenceFieldWidthPercent) {
        double maturityScore = calculateMaturityScore(trackedCell, fps);
        double widthTraversalScore = calculateWidthTraversalConfidenceScore(
                trackedCell,
                frameWidth,
                confidenceFieldWidthPercent);
        double continuityScore = calculateContinuityScore(status);
        return clamp01((0.40 * maturityScore) + (0.35 * widthTraversalScore) + (0.25 * continuityScore));
    }

    private double calculateMaturityScore(TrackedCell trackedCell, double fps) {
        int trackedFrames = trackedCell == null ? 0 : trackedCell.history().size();
        double matureFrameThreshold = Math.max(12.0, fps > 0.0 ? fps * 0.75 : 18.0);
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
        double firstX = history.get(0).centroidX();
        double lastX = history.get(history.size() - 1).centroidX();
        double horizontalProgress = Math.max(0.0, lastX - firstX);
        return clamp01(horizontalProgress / frameWidth);
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
