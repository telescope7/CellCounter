package com.prolymphname.cellcounter.application;

public record TrackingQualitySummary(
        int confidencePercent,
        int activeTracks,
        int highConfidenceTracks,
        int watchTracks,
        int occlusionRiskTracks) {

    public static TrackingQualitySummary empty() {
        return new TrackingQualitySummary(0, 0, 0, 0, 0);
    }
}
