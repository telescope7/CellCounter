package com.prolymphname.cellcounter.analysis;

import org.junit.Test;
import org.opencv.core.Rect;

import static org.junit.Assert.*;

public class RightEdgeExitPolicyTest {

    private final RightEdgeExitPolicy policy = new RightEdgeExitPolicy();

    @Test
    public void shouldRetire_returnsFalseWhenFeatureDisabled() {
        assertFalse(policy.shouldRetire(new Rect(180, 20, 12, 10), 2, 200, 0));
    }

    @Test
    public void shouldRetire_requiresTwoMissesInsideExitZone() {
        Rect bboxNearRightEdge = new Rect(178, 20, 12, 10);
        assertFalse(policy.shouldRetire(bboxNearRightEdge, 1, 200, 8));
        assertTrue(policy.shouldRetire(bboxNearRightEdge, 2, 200, 8));
    }

    @Test
    public void shouldRetire_immediatelyWhenTouchingRightBorder() {
        Rect bboxTouchingBorder = new Rect(190, 20, 10, 10);
        assertTrue(policy.shouldRetire(bboxTouchingBorder, 1, 200, 8));
    }

    @Test
    public void shouldRetire_returnsFalseAwayFromExitZone() {
        Rect bboxMidField = new Rect(120, 20, 10, 10);
        assertFalse(policy.shouldRetire(bboxMidField, 4, 200, 8));
    }
}
