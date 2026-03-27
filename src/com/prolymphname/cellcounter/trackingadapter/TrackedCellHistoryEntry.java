package com.prolymphname.cellcounter.trackingadapter;

public record TrackedCellHistoryEntry(
        int frame,
        int upperLeftX,
        int upperLeftY,
        int lowerRightX,
        int lowerRightY) {

    public double centroidX() {
        return (upperLeftX + lowerRightX) / 2.0;
    }

    public double centroidY() {
        return (upperLeftY + lowerRightY) / 2.0;
    }
}
