package com.prolymphname.cellcounter.analysis;

import org.junit.Test;
import org.opencv.core.Point;
import org.opencv.core.Rect;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class TrackedOverlayTest {

    @Test
    public void labelText_includesBoundingSizeWhenRequested() {
        TrackedOverlay overlay = new TrackedOverlay(
                42,
                new Rect(10, 20, 18, 12),
                new Point(19, 26),
                TrackVisualState.STABLE,
                0,
                false,
                List.of());

        assertEquals("ID 42 18x12 px", overlay.labelText(true));
        assertEquals("ID 42", overlay.labelText(false));
    }

    @Test
    public void labelText_keepsStateTagsWhenIncludingBoundingSize() {
        TrackedOverlay overlay = new TrackedOverlay(
                7,
                new Rect(5, 8, 14, 9),
                new Point(12, 12),
                TrackVisualState.MISSED,
                3,
                true,
                List.of());

        assertEquals("ID 7 14x9 px OCC MISS 3", overlay.labelText(true));
    }
}
