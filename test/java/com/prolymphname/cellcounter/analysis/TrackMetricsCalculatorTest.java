package com.prolymphname.cellcounter.analysis;

import com.prolymphname.cellcounter.trackingadapter.TrackMetrics;
import com.prolymphname.cellcounter.trackingadapter.TrackedCell;
import com.prolymphname.cellcounter.trackingadapter.TrackedCellHistoryEntry;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class TrackMetricsCalculatorTest {
    private final TrackMetricsCalculator calculator = new TrackMetricsCalculator();

    @Test
    public void calculate_returnsEmptyMetricsForEmptyHistory() {
        TrackedCell trackedCell = new TrackedCell(7, 1, 0.0, 3, List.of());

        TrackMetrics metrics = calculator.calculate(trackedCell, 30.0);

        assertEquals(0.0, metrics.totalDistance(), 0.001);
        assertEquals(0.0, metrics.avgFrameDistance(), 0.001);
        assertEquals(0.0, metrics.medianFrameDistance(), 0.001);
        assertEquals(0, metrics.framesTracked());
        assertEquals(3, metrics.framesMissed());
        assertEquals(0.0, metrics.speed(), 0.001);
    }

    @Test
    public void calculate_computesDistanceAveragesMedianAndSpeed() {
        TrackedCell trackedCell = new TrackedCell(
                11,
                2,
                0.5,
                2,
                List.of(
                        new TrackedCellHistoryEntry(2, 0, 0, 2, 2),
                        new TrackedCellHistoryEntry(3, 3, 0, 5, 2),
                        new TrackedCellHistoryEntry(4, 3, 4, 5, 6)));

        TrackMetrics metrics = calculator.calculate(trackedCell, 2.0);

        assertEquals(7.0, metrics.totalDistance(), 0.001);
        assertEquals(3.5, metrics.avgFrameDistance(), 0.001);
        assertEquals(3.5, metrics.medianFrameDistance(), 0.001);
        assertEquals(3, metrics.framesTracked());
        assertEquals(2, metrics.framesMissed());
        assertEquals(7.0 / 1.5, metrics.speed(), 0.001);
    }
}
