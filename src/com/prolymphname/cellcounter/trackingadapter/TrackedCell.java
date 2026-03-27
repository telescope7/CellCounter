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
}
