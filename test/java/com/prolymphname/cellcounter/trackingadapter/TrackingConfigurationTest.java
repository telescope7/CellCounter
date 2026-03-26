package com.prolymphname.cellcounter.trackingadapter;

import org.junit.Test;
import static org.junit.Assert.*;

public class TrackingConfigurationTest {

    // ------------------------------------------------------------------ defaults

    @Test
    public void defaults_returnsGreedyAlgorithm() {
        assertEquals(TrackerAlgorithm.GREEDY, TrackingConfiguration.defaults().getTrackerAlgorithm());
    }

    @Test
    public void defaults_hasPositiveMaxFramesDisappeared() {
        assertTrue(TrackingConfiguration.defaults().getMaxFramesDisappeared() > 0);
    }

    @Test
    public void defaults_hasPositiveMog2HistoryFrames() {
        assertTrue(TrackingConfiguration.defaults().getMog2HistoryFrames() > 0);
    }

    @Test
    public void defaults_maxVerticalDisplacementIsReasonable() {
        // Should be ≤ a typical video height (~1080) — no longer the old 43035 sentinel
        double v = TrackingConfiguration.defaults().getMaxVerticalDisplacementPixels();
        assertTrue("maxVerticalDisplacementPixels should be > 0 and <= 1080, was: " + v,
                v > 0 && v <= 1080);
    }

    @Test
    public void defaults_mog2HistoryFramesIsReasonable() {
        // Should be ≤ 600 — no longer the old 1108 value
        int h = TrackingConfiguration.defaults().getMog2HistoryFrames();
        assertTrue("mog2HistoryFrames should be > 0 and <= 600, was: " + h,
                h > 0 && h <= 600);
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
                TrackerAlgorithm.GREEDY);
        assertEquals(255.0, cfg.normalized().getNormalizedMaskThreshold(), 0.001);
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
                b.getNormalizedMaskThreshold(), TrackerAlgorithm.GREEDY);
    }
}
