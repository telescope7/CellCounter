package com.prolymphname.cellcounter.analysis;

import org.opencv.core.Rect;

public final class RightEdgeExitPolicy {
    private static final int BORDER_TOUCH_TOLERANCE_PX = 1;
    private static final int EXIT_ZONE_MISS_BUDGET = 2;

    public boolean shouldRetire(Rect lastBoundingBox, int disappearedFrames, int frameWidth, int rightEdgeExitZonePercent) {
        if (lastBoundingBox == null || disappearedFrames <= 0 || frameWidth <= 0 || rightEdgeExitZonePercent <= 0) {
            return false;
        }

        double zoneStartX = frameWidth * (1.0 - (rightEdgeExitZonePercent / 100.0));
        double rightEdgeX = lastBoundingBox.x + lastBoundingBox.width;
        boolean touchesRightBorder = rightEdgeX >= (frameWidth - BORDER_TOUCH_TOLERANCE_PX);
        boolean insideExitZone = rightEdgeX >= zoneStartX;

        if (!insideExitZone && !touchesRightBorder) {
            return false;
        }

        return touchesRightBorder ? disappearedFrames >= 1 : disappearedFrames >= EXIT_ZONE_MISS_BUDGET;
    }
}
