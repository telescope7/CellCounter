package com.prolymphname.cellcounter.application;

import com.prolymphname.cellcounter.trackingadapter.TrackStatusSnapshot;
import com.prolymphname.cellcounter.trackingadapter.TrackedCell;
import com.prolymphname.cellcounter.trackingadapter.TrackedCellHistoryEntry;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class TrackingQualityCalculatorTest {
    private final TrackingQualityCalculator calculator = new TrackingQualityCalculator();

    @Test
    public void calculate_returnsEmptySummaryWhenNoActiveTracksExist() {
        TrackingQualitySummary summary = calculator.calculate(List.of(), List.of(), 400, 30.0, 60);

        assertEquals(0, summary.confidencePercent());
        assertEquals(0, summary.activeTracks());
        assertEquals(0, summary.highConfidenceTracks());
        assertEquals(0, summary.watchTracks());
        assertEquals(0, summary.occlusionRiskTracks());
    }

    @Test
    public void calculate_blendsMaturityCoverageAndCurrentRiskIntoConfidenceSummary() {
        TrackedCell strongTrack = new TrackedCell(
                101,
                0,
                0.0,
                0,
                buildHistory(30, 10, 210, 0));
        TrackedCell weakTrack = new TrackedCell(
                202,
                0,
                0.0,
                2,
                buildHistory(8, 20, 80, 10));

        TrackingQualitySummary summary = calculator.calculate(
                List.of(strongTrack, weakTrack),
                List.of(
                        new TrackStatusSnapshot(101, 0, false),
                        new TrackStatusSnapshot(202, 2, true)),
                400,
                30.0,
                60);

        assertEquals(71, summary.confidencePercent());
        assertEquals(2, summary.activeTracks());
        assertEquals(1, summary.highConfidenceTracks());
        assertEquals(1, summary.watchTracks());
        assertEquals(1, summary.occlusionRiskTracks());
    }

    private List<TrackedCellHistoryEntry> buildHistory(int frames, int startX, int endX, int y) {
        List<TrackedCellHistoryEntry> history = new ArrayList<>();
        for (int i = 0; i < frames; i++) {
            double progress = frames <= 1 ? 0.0 : (double) i / (frames - 1);
            int upperLeftX = (int) Math.round(startX + ((endX - startX) * progress));
            history.add(new TrackedCellHistoryEntry(i + 1, upperLeftX, y, upperLeftX + 20, y + 20, 100.0));
        }
        return history;
    }
}
