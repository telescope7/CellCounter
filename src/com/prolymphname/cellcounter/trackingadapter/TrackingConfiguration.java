package com.prolymphname.cellcounter.trackingadapter;

public final class TrackingConfiguration {
    private final int maxFramesDisappeared;
    private final double minContourArea;
    private final double maxRectCircumference;
    private final double maxVerticalDisplacementPixels;
    private final double minHorizontalMovementPixels;
    private final double maxAssociationDistancePixels;
    private final int mog2HistoryFrames;
    private final double mog2VarThreshold;
    private final boolean mog2DetectShadows;
    private final int morphologyKernelSize;
    private final int morphologyOpenIterations;
    private final int morphologyDilateIterations;
    private final double normalizedMaskThreshold;
    private final int confidenceFieldWidthPercent;
    private final int rightEdgeExitZonePercent;
    private final TrackerAlgorithm trackerAlgorithm;

    public TrackingConfiguration(
            int maxFramesDisappeared,
            double minContourArea,
            double maxRectCircumference,
            double maxVerticalDisplacementPixels,
            double minHorizontalMovementPixels,
            double maxAssociationDistancePixels,
            int mog2HistoryFrames,
            double mog2VarThreshold,
            boolean mog2DetectShadows,
            int morphologyKernelSize,
            int morphologyOpenIterations,
            int morphologyDilateIterations,
            double normalizedMaskThreshold,
            int confidenceFieldWidthPercent,
            int rightEdgeExitZonePercent,
            TrackerAlgorithm trackerAlgorithm) {
        this.maxFramesDisappeared = maxFramesDisappeared;
        this.minContourArea = minContourArea;
        this.maxRectCircumference = maxRectCircumference;
        this.maxVerticalDisplacementPixels = maxVerticalDisplacementPixels;
        this.minHorizontalMovementPixels = minHorizontalMovementPixels;
        this.maxAssociationDistancePixels = maxAssociationDistancePixels;
        this.mog2HistoryFrames = mog2HistoryFrames;
        this.mog2VarThreshold = mog2VarThreshold;
        this.mog2DetectShadows = mog2DetectShadows;
        this.morphologyKernelSize = morphologyKernelSize;
        this.morphologyOpenIterations = morphologyOpenIterations;
        this.morphologyDilateIterations = morphologyDilateIterations;
        this.normalizedMaskThreshold = normalizedMaskThreshold;
        this.confidenceFieldWidthPercent = confidenceFieldWidthPercent;
        this.rightEdgeExitZonePercent = rightEdgeExitZonePercent;
        this.trackerAlgorithm = (trackerAlgorithm != null) ? trackerAlgorithm : TrackerAlgorithm.GREEDY;
    }

    public static TrackingConfiguration defaults() {
        return TrackingConfigurationDefaultsLoader.loadRequiredDefaults();
    }

    public TrackingConfiguration normalized() {
        int normalizedKernel = Math.max(1, morphologyKernelSize);
        if (normalizedKernel % 2 == 0) {
            normalizedKernel += 1;
        }

        return new TrackingConfiguration(
                Math.max(1, maxFramesDisappeared),
                Math.max(0.0, minContourArea),
                Math.max(1.0, maxRectCircumference),
                Math.max(0.0, maxVerticalDisplacementPixels),
                minHorizontalMovementPixels,
                Math.max(1.0, maxAssociationDistancePixels),
                Math.max(1, mog2HistoryFrames),
                Math.max(0.01, mog2VarThreshold),
                mog2DetectShadows,
                normalizedKernel,
                Math.max(0, morphologyOpenIterations),
                Math.max(0, morphologyDilateIterations),
                Math.max(0.0, Math.min(255.0, normalizedMaskThreshold)),
                Math.max(1, Math.min(100, confidenceFieldWidthPercent)),
                Math.max(0, Math.min(100, rightEdgeExitZonePercent)),
                (trackerAlgorithm != null) ? trackerAlgorithm : TrackerAlgorithm.GREEDY);
    }

    public int getMaxFramesDisappeared() {
        return maxFramesDisappeared;
    }

    public double getMinContourArea() {
        return minContourArea;
    }

    public double getMaxRectCircumference() {
        return maxRectCircumference;
    }

    public double getMaxVerticalDisplacementPixels() {
        return maxVerticalDisplacementPixels;
    }

    public double getMinHorizontalMovementPixels() {
        return minHorizontalMovementPixels;
    }

    public double getMaxAssociationDistancePixels() {
        return maxAssociationDistancePixels;
    }

    public int getMog2HistoryFrames() {
        return mog2HistoryFrames;
    }

    public double getMog2VarThreshold() {
        return mog2VarThreshold;
    }

    public boolean isMog2DetectShadows() {
        return mog2DetectShadows;
    }

    public int getMorphologyKernelSize() {
        return morphologyKernelSize;
    }

    public int getMorphologyOpenIterations() {
        return morphologyOpenIterations;
    }

    public int getMorphologyDilateIterations() {
        return morphologyDilateIterations;
    }

    public double getNormalizedMaskThreshold() {
        return normalizedMaskThreshold;
    }

    public int getConfidenceFieldWidthPercent() {
        return confidenceFieldWidthPercent;
    }

    public int getRightEdgeExitZonePercent() {
        return rightEdgeExitZonePercent;
    }

    public TrackerAlgorithm getTrackerAlgorithm() {
        return trackerAlgorithm;
    }
}
