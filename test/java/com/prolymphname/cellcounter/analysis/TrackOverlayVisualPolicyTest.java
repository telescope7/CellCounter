package com.prolymphname.cellcounter.analysis;

import org.junit.Test;
import org.opencv.core.Point;
import org.opencv.core.Rect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TrackOverlayVisualPolicyTest {
    private final TrackOverlayVisualPolicy policy = new TrackOverlayVisualPolicy();

    @Test
    public void resolveState_marksMissedTracksAsMissed() {
        assertEquals(TrackVisualState.MISSED, policy.resolveState(false, 2));
    }

    @Test
    public void resolveState_marksFreshTracksAsNew() {
        assertEquals(TrackVisualState.NEW, policy.resolveState(true, 0));
    }

    @Test
    public void resolveState_marksNormalTracksAsStable() {
        assertEquals(TrackVisualState.STABLE, policy.resolveState(false, 0));
    }

    @Test
    public void isOcclusionRisk_trueWhenBoxesOverlap() {
        assertTrue(policy.isOcclusionRisk(
                new Rect(10, 10, 12, 12),
                new Point(16, 16),
                new Rect(18, 16, 12, 12),
                new Point(24, 22),
                175.0));
    }

    @Test
    public void isOcclusionRisk_trueWhenCentroidsAreVeryClose() {
        assertTrue(policy.isOcclusionRisk(
                new Rect(10, 10, 12, 12),
                new Point(16, 16),
                new Rect(28, 12, 12, 12),
                new Point(22, 17),
                175.0));
    }

    @Test
    public void isOcclusionRisk_falseWhenTracksAreFarApart() {
        assertFalse(policy.isOcclusionRisk(
                new Rect(10, 10, 12, 12),
                new Point(16, 16),
                new Rect(120, 120, 12, 12),
                new Point(126, 126),
                175.0));
    }
}
