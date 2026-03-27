package com.prolymphname.cellcounter.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ChartRefreshControllerTest {
    private final ChartRefreshController controller = new ChartRefreshController();

    @Test
    public void configureForFps_roundsToOneSecondFrameInterval() {
        controller.configureForFps(29.97);

        assertEquals(30, controller.getRefreshIntervalFrames());
    }

    @Test
    public void configureForFps_defaultsWhenInvalid() {
        controller.configureForFps(0.0);

        assertEquals(30, controller.getRefreshIntervalFrames());
    }

    @Test
    public void shouldRefreshAtFrame_onlyAfterIntervalHasElapsed() {
        controller.configureForFps(12.0);

        assertFalse(controller.shouldRefreshAtFrame(11));
        assertTrue(controller.shouldRefreshAtFrame(12));

        controller.markRefreshedAtFrame(12);

        assertFalse(controller.shouldRefreshAtFrame(23));
        assertTrue(controller.shouldRefreshAtFrame(24));
    }

    @Test
    public void markRefreshedAtFrame_clampsNegativeInput() {
        controller.configureForFps(10.0);
        controller.markRefreshedAtFrame(-5);

        assertFalse(controller.shouldRefreshAtFrame(9));
        assertTrue(controller.shouldRefreshAtFrame(10));
    }
}
