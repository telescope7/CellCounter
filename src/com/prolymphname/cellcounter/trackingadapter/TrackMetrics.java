package com.prolymphname.cellcounter.trackingadapter;

public record TrackMetrics(
        double totalDistance,
        double distanceToCross,
        double distanceAfterCross,
        double avgFrameDistance,
        double medianFrameDistance,
        int framesTracked,
        int framesMissed,
        double speed) {

    public static TrackMetrics empty(int framesMissed) {
        return new TrackMetrics(0.0, 0.0, 0.0, 0.0, 0.0, 0, framesMissed, 0.0);
    }
}
