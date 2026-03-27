package com.prolymphname.cellcounter.ui;

/**
 * Controls how often live charts should refresh relative to processed video frames.
 * Explicit UI actions can still force immediate refreshes outside this policy.
 */
public final class ChartRefreshController {
    private static final int DEFAULT_REFRESH_INTERVAL_FRAMES = 30;

    private int refreshIntervalFrames = DEFAULT_REFRESH_INTERVAL_FRAMES;
    private int lastRefreshedFrameNumber = 0;

    public void configureForFps(double fps) {
        if (fps > 0.0) {
            refreshIntervalFrames = Math.max(1, (int) Math.round(fps));
        } else {
            refreshIntervalFrames = DEFAULT_REFRESH_INTERVAL_FRAMES;
        }
        lastRefreshedFrameNumber = 0;
    }

    public int getRefreshIntervalFrames() {
        return refreshIntervalFrames;
    }

    public boolean shouldRefreshAtFrame(int currentFrameNumber) {
        if (currentFrameNumber <= 0) {
            return false;
        }
        return currentFrameNumber - lastRefreshedFrameNumber >= refreshIntervalFrames;
    }

    public void markRefreshedAtFrame(int currentFrameNumber) {
        lastRefreshedFrameNumber = Math.max(0, currentFrameNumber);
    }
}
