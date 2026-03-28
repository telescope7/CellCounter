package com.prolymphname.cellcounter.trackingadapter;

public record TrackStatusSnapshot(
        int cellId,
        int missedFrames,
        boolean occlusionRisk) {

    public boolean watchState() {
        return missedFrames > 0 || occlusionRisk;
    }
}
