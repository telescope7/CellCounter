package com.prolymphname.cellcounter.analysis;

import com.prolymphname.cellcounter.trackingadapter.TrackMetrics;
import com.prolymphname.cellcounter.trackingadapter.TrackedCell;
import com.prolymphname.cellcounter.trackingadapter.TrackedCellHistoryEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TrackMetricsCalculator {
    public TrackMetrics calculate(TrackedCell trackedCell, double fps) {
        if (trackedCell == null || trackedCell.history().isEmpty()) {
            return TrackMetrics.empty(trackedCell == null ? 0 : trackedCell.missedFrames());
        }

        List<TrackedCellHistoryEntry> history = trackedCell.history();
        double totalDistance = 0.0;
        List<Double> frameDistances = new ArrayList<>();

        for (int i = 1; i < history.size(); i++) {
            TrackedCellHistoryEntry previous = history.get(i - 1);
            TrackedCellHistoryEntry current = history.get(i);
            double distance = Math.hypot(
                    current.centroidX() - previous.centroidX(),
                    current.centroidY() - previous.centroidY());
            totalDistance += distance;
            frameDistances.add(distance);
        }

        double avgMove = frameDistances.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double medianMove = median(frameDistances);
        int framesTracked = history.size();
        double timeElapsedTracked = fps > 0 ? framesTracked / fps : 0.0;
        double overallSpeed = timeElapsedTracked > 0.0 ? totalDistance / timeElapsedTracked : 0.0;

        return new TrackMetrics(
                totalDistance,
                0.0,
                0.0,
                avgMove,
                medianMove,
                framesTracked,
                trackedCell.missedFrames(),
                overallSpeed);
    }

    private double median(List<Double> values) {
        if (values.isEmpty()) {
            return 0.0;
        }
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 0) {
            return (sorted.get(middle - 1) + sorted.get(middle)) / 2.0;
        }
        return sorted.get(middle);
    }
}
