package com.prolymphname.cellcounter.trackingadapter;

import java.util.List;

public record TrackedCell(
        int cellId,
        int startFrame,
        double startTime,
        int missedFrames,
        List<TrackedCellHistoryEntry> history) {

    public TrackedCell {
        history = history == null ? List.of() : List.copyOf(history);
    }

    public boolean hasHistory() {
        return !history.isEmpty();
    }

    public double meanContourAreaPx() {
        if (history.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        for (TrackedCellHistoryEntry item : history) {
            sum += item.contourAreaPx();
        }
        return sum / history.size();
    }

    public double medianContourAreaPx() {
        if (history.isEmpty()) {
            return 0.0;
        }
        double[] values = new double[history.size()];
        for (int i = 0; i < history.size(); i++) {
            values[i] = history.get(i).contourAreaPx();
        }
        java.util.Arrays.sort(values);
        int mid = values.length / 2;
        if (values.length % 2 == 0) {
            return (values[mid - 1] + values[mid]) / 2.0;
        }
        return values[mid];
    }

    public double lastContourAreaPx() {
        if (history.isEmpty()) {
            return 0.0;
        }
        return history.get(history.size() - 1).contourAreaPx();
    }
}
