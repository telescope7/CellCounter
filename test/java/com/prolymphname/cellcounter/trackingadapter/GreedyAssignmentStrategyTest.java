package com.prolymphname.cellcounter.trackingadapter;

import org.junit.Test;
import static org.junit.Assert.*;

public class GreedyAssignmentStrategyTest {

    private final AssignmentStrategy strategy = new GreedyAssignmentStrategy();

    // ------------------------------------------------------------------ edge cases

    @Test
    public void assign_emptyMatrix_returnsEmptyArray() {
        int[] result = strategy.assign(new double[0][0]);
        assertEquals(0, result.length);
    }

    @Test
    public void assign_noDetections_allUnmatched() {
        double[][] D = new double[3][0];
        int[] result = strategy.assign(D);
        assertEquals(3, result.length);
        for (int r : result) assertEquals(-1, r);
    }

    @Test
    public void assign_allForbidden_allUnmatched() {
        double[][] D = {
            {Double.MAX_VALUE, Double.MAX_VALUE},
            {Double.MAX_VALUE, Double.MAX_VALUE}
        };
        int[] result = strategy.assign(D);
        assertEquals(-1, result[0]);
        assertEquals(-1, result[1]);
    }

    // ------------------------------------------------------------------ basic matching

    @Test
    public void assign_singlePair_matched() {
        double[][] D = {{25.0}};
        int[] result = strategy.assign(D);
        assertEquals(0, result[0]);
    }

    @Test
    public void assign_identityMatrix_diagonalAssigned() {
        double[][] D = {
            {1.0, 100.0, 100.0},
            {100.0, 4.0, 100.0},
            {100.0, 100.0, 9.0}
        };
        int[] result = strategy.assign(D);
        assertEquals(0, result[0]);
        assertEquals(1, result[1]);
        assertEquals(2, result[2]);
    }

    @Test
    public void assign_twoTracksOneDetection_nearestWins() {
        // Track 0 is farther (100), track 1 is closer (4) — greedy assigns to nearest first
        double[][] D = {
            {100.0},
            {4.0}
        };
        int[] result = strategy.assign(D);
        assertEquals("closer track (1) should get detection 0", 0, result[1]);
        assertEquals("farther track (0) should be unmatched", -1, result[0]);
    }

    @Test
    public void assign_moreDetectionsThanTracks_allTracksMatched() {
        // 2 tracks, 3 detections: each track gets its nearest
        double[][] D = {
            {50.0, 1.0, 200.0},
            {2.0, 300.0, 400.0}
        };
        int[] result = strategy.assign(D);
        assertEquals("track 0 nearest det=1", 1, result[0]);
        assertEquals("track 1 nearest det=0", 0, result[1]);
    }

    @Test
    public void assign_eachDetectionUsedAtMostOnce() {
        // Both tracks prefer detection 0; track 0 is closer so it wins
        double[][] D = {
            {1.0, 100.0},
            {2.0, 50.0}
        };
        int[] result = strategy.assign(D);
        // track 0 gets det 0 (dist 1), track 1 gets det 1 (dist 50)
        assertEquals(0, result[0]);
        assertEquals(1, result[1]);
    }

    @Test
    public void assign_forbiddenEntrySkipped() {
        double[][] D = {
            {Double.MAX_VALUE, 9.0},
            {4.0, Double.MAX_VALUE}
        };
        int[] result = strategy.assign(D);
        assertEquals("track 0 must skip forbidden col 0", 1, result[0]);
        assertEquals("track 1 must skip forbidden col 1", 0, result[1]);
    }

    @Test
    public void assign_partialForbidden_bestValidAssigned() {
        double[][] D = {
            {Double.MAX_VALUE, 16.0, 25.0},
            {9.0, Double.MAX_VALUE, 36.0}
        };
        int[] result = strategy.assign(D);
        assertEquals("track 0 gets det 1 (16 < 25)", 1, result[0]);
        assertEquals("track 1 gets det 0 (9, only valid)", 0, result[1]);
    }
}
