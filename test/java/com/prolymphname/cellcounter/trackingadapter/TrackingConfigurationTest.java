package com.prolymphname.cellcounter.trackingadapter;

import org.junit.Test;
import static org.junit.Assert.*;

public class TrackingConfigurationTest {

    // ------------------------------------------------------------------ defaults

    @Test
    public void defaults_matchCellCounterProperties() {
        TrackingConfigurationDefaultsLoader.clearCacheForTests();
        TrackingConfiguration defaults = TrackingConfiguration.defaults();

        assertEquals(7, defaults.getMaxFramesDisappeared());
        assertEquals(15.0, defaults.getMinContourArea(), 0.0001);
        assertEquals(219.0, defaults.getMaxRectCircumference(), 0.0001);
        assertEquals(10.0, defaults.getMaxVerticalDisplacementPixels(), 0.0001);
        assertEquals(-2.0, defaults.getMinHorizontalMovementPixels(), 0.0001);
        assertEquals(109.0, defaults.getMaxAssociationDistancePixels(), 0.0001);
        assertEquals(200, defaults.getMog2HistoryFrames());
        assertEquals(17.0, defaults.getMog2VarThreshold(), 0.0001);
        assertFalse(defaults.isMog2DetectShadows());
        assertEquals(5, defaults.getMorphologyKernelSize());
        assertEquals(1, defaults.getMorphologyOpenIterations());
        assertEquals(1, defaults.getMorphologyDilateIterations());
        assertEquals(19.0, defaults.getNormalizedMaskThreshold(), 0.0001);
        assertEquals(60, defaults.getConfidenceFieldWidthPercent());
        assertEquals(5, defaults.getRightEdgeExitZonePercent());
        assertEquals(TrackerAlgorithm.GREEDY, defaults.getTrackerAlgorithm());
    }

    // ------------------------------------------------------------------ normalized

    @Test
    public void normalized_enforcesOddKernelSize_evenInput() {
        TrackingConfiguration cfg = makeWith(4 /* even kernel */);
        assertEquals(5, cfg.normalized().getMorphologyKernelSize());
    }

    @Test
    public void normalized_enforcesOddKernelSize_oddInput() {
        TrackingConfiguration cfg = makeWith(3 /* odd kernel */);
        assertEquals(3, cfg.normalized().getMorphologyKernelSize());
    }

    @Test
    public void normalized_clampsNegativeMaxFramesDisappeared() {
        TrackingConfiguration base = TrackingConfiguration.defaults();
        TrackingConfiguration cfg = new TrackingConfiguration(
                -5, base.getMinContourArea(), base.getMaxRectCircumference(),
                base.getMaxVerticalDisplacementPixels(), base.getMinHorizontalMovementPixels(),
                base.getMaxAssociationDistancePixels(), base.getMog2HistoryFrames(),
                base.getMog2VarThreshold(), base.isMog2DetectShadows(),
                base.getMorphologyKernelSize(), base.getMorphologyOpenIterations(),
                base.getMorphologyDilateIterations(), base.getNormalizedMaskThreshold(),
                base.getConfidenceFieldWidthPercent(), base.getRightEdgeExitZonePercent(),
                TrackerAlgorithm.GREEDY);
        assertEquals(1, cfg.normalized().getMaxFramesDisappeared());
    }

    @Test
    public void normalized_clampsMaskThresholdAbove255() {
        TrackingConfiguration base = TrackingConfiguration.defaults();
        TrackingConfiguration cfg = new TrackingConfiguration(
                base.getMaxFramesDisappeared(), base.getMinContourArea(),
                base.getMaxRectCircumference(), base.getMaxVerticalDisplacementPixels(),
                base.getMinHorizontalMovementPixels(), base.getMaxAssociationDistancePixels(),
                base.getMog2HistoryFrames(), base.getMog2VarThreshold(), base.isMog2DetectShadows(),
                base.getMorphologyKernelSize(), base.getMorphologyOpenIterations(),
                base.getMorphologyDilateIterations(), 999.0,
                base.getConfidenceFieldWidthPercent(), base.getRightEdgeExitZonePercent(),
                TrackerAlgorithm.GREEDY);
        assertEquals(255.0, cfg.normalized().getNormalizedMaskThreshold(), 0.001);
    }

    @Test
    public void normalized_clampsRightEdgeExitZonePercent() {
        TrackingConfiguration base = TrackingConfiguration.defaults();
        TrackingConfiguration cfg = new TrackingConfiguration(
                base.getMaxFramesDisappeared(), base.getMinContourArea(),
                base.getMaxRectCircumference(), base.getMaxVerticalDisplacementPixels(),
                base.getMinHorizontalMovementPixels(), base.getMaxAssociationDistancePixels(),
                base.getMog2HistoryFrames(), base.getMog2VarThreshold(), base.isMog2DetectShadows(),
                base.getMorphologyKernelSize(), base.getMorphologyOpenIterations(),
                base.getMorphologyDilateIterations(), base.getNormalizedMaskThreshold(),
                base.getConfidenceFieldWidthPercent(), 250,
                TrackerAlgorithm.GREEDY);
        assertEquals(100, cfg.normalized().getRightEdgeExitZonePercent());
    }

    @Test
    public void normalized_preservesTrackerAlgorithm_hungarian() {
        TrackingConfiguration base = TrackingConfiguration.defaults();
        TrackingConfiguration cfg = new TrackingConfiguration(
                base.getMaxFramesDisappeared(), base.getMinContourArea(),
                base.getMaxRectCircumference(), base.getMaxVerticalDisplacementPixels(),
                base.getMinHorizontalMovementPixels(), base.getMaxAssociationDistancePixels(),
                base.getMog2HistoryFrames(), base.getMog2VarThreshold(), base.isMog2DetectShadows(),
                base.getMorphologyKernelSize(), base.getMorphologyOpenIterations(),
                base.getMorphologyDilateIterations(), base.getNormalizedMaskThreshold(),
                base.getConfidenceFieldWidthPercent(), base.getRightEdgeExitZonePercent(),
                TrackerAlgorithm.HUNGARIAN);
        assertEquals(TrackerAlgorithm.HUNGARIAN, cfg.normalized().getTrackerAlgorithm());
    }

    @Test
    public void normalized_nullAlgorithmDefaultsToGreedy() {
        TrackingConfiguration base = TrackingConfiguration.defaults();
        TrackingConfiguration cfg = new TrackingConfiguration(
                base.getMaxFramesDisappeared(), base.getMinContourArea(),
                base.getMaxRectCircumference(), base.getMaxVerticalDisplacementPixels(),
                base.getMinHorizontalMovementPixels(), base.getMaxAssociationDistancePixels(),
                base.getMog2HistoryFrames(), base.getMog2VarThreshold(), base.isMog2DetectShadows(),
                base.getMorphologyKernelSize(), base.getMorphologyOpenIterations(),
                base.getMorphologyDilateIterations(), base.getNormalizedMaskThreshold(),
                base.getConfidenceFieldWidthPercent(), base.getRightEdgeExitZonePercent(),
                null);
        assertEquals(TrackerAlgorithm.GREEDY, cfg.getTrackerAlgorithm());
        assertEquals(TrackerAlgorithm.GREEDY, cfg.normalized().getTrackerAlgorithm());
    }

    // ------------------------------------------------------------------ TrackerAlgorithm.fromString

    @Test
    public void fromString_parsesHungarian_caseInsensitive() {
        assertEquals(TrackerAlgorithm.HUNGARIAN, TrackerAlgorithm.fromString("HUNGARIAN"));
        assertEquals(TrackerAlgorithm.HUNGARIAN, TrackerAlgorithm.fromString("hungarian"));
        assertEquals(TrackerAlgorithm.HUNGARIAN, TrackerAlgorithm.fromString("Hungarian"));
    }

    @Test
    public void fromString_unknownValueDefaultsToGreedy() {
        assertEquals(TrackerAlgorithm.GREEDY, TrackerAlgorithm.fromString("nonsense"));
        assertEquals(TrackerAlgorithm.GREEDY, TrackerAlgorithm.fromString(null));
        assertEquals(TrackerAlgorithm.GREEDY, TrackerAlgorithm.fromString(""));
    }

    // ------------------------------------------------------------------ helper

    private TrackingConfiguration makeWith(int kernelSize) {
        TrackingConfiguration b = TrackingConfiguration.defaults();
        return new TrackingConfiguration(
                b.getMaxFramesDisappeared(), b.getMinContourArea(), b.getMaxRectCircumference(),
                b.getMaxVerticalDisplacementPixels(), b.getMinHorizontalMovementPixels(),
                b.getMaxAssociationDistancePixels(), b.getMog2HistoryFrames(),
                b.getMog2VarThreshold(), b.isMog2DetectShadows(),
                kernelSize,
                b.getMorphologyOpenIterations(), b.getMorphologyDilateIterations(),
                b.getNormalizedMaskThreshold(), b.getConfidenceFieldWidthPercent(), b.getRightEdgeExitZonePercent(), TrackerAlgorithm.GREEDY);
    }
}
