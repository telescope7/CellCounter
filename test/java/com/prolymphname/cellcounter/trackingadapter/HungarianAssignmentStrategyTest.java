package com.prolymphname.cellcounter.trackingadapter;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.HashSet;

public class HungarianAssignmentStrategyTest {

    private final AssignmentStrategy strategy = new HungarianAssignmentStrategy();

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
    public void assign_optimalVsGreedy_hungarianWins() {
        // Greedy picks track0→det0 (cost 1) then track1→det1 (cost 10) = 11.
        // Optimal picks track0→det1 (cost 2) then track1→det0 (cost 2) = 4 — globally better.
        double[][] D = {
            {1.0, 2.0},
            {2.0, 10.0}
        };
        int[] result = strategy.assign(D);
        // Hungarian should find global optimum: total cost 4 (0→1, 1→0)
        // (or 3 if 0→0,1→1 — let's compute: 1+10=11 vs 2+2=4, so 0→1, 1→0)
        int t0 = result[0];
        int t1 = result[1];
        double cost = D[0][t0] + D[1][t1];
        assertEquals("Hungarian should find minimum cost assignment of 4.0", 4.0, cost, 0.001);
    }

    @Test
    public void assign_twoTracksOneDetection_nearestWins() {
        double[][] D = {
            {100.0},
            {4.0}
        };
        int[] result = strategy.assign(D);
        assertEquals("nearer track (1) gets det 0", 0, result[1]);
        assertEquals("farther track (0) unmatched", -1, result[0]);
    }

    @Test
    public void assign_moreDetectionsThanTracks_allTracksMatched() {
        double[][] D = {
            {50.0, 1.0, 200.0},
            {2.0, 300.0, 400.0}
        };
        int[] result = strategy.assign(D);
        // Global optimum: 0→1 (1) + 1→0 (2) = 3; vs 0→0 (50) + 1→2 (400) = 450
        assertEquals(1, result[0]);
        assertEquals(0, result[1]);
    }

    @Test
    public void assign_eachDetectionUsedAtMostOnce() {
        double[][] D = {
            {1.0, 100.0},
            {2.0, 50.0}
        };
        int[] result = strategy.assign(D);
        // Check no two tracks share a detection
        HashSet<Integer> used = new HashSet<>();
        for (int r : result) {
            if (r != -1) {
                assertFalse("Detection " + r + " used twice", used.contains(r));
                used.add(r);
            }
        }
    }

    @Test
    public void assign_forbiddenEntrySkipped() {
        double[][] D = {
            {Double.MAX_VALUE, 9.0},
            {4.0, Double.MAX_VALUE}
        };
        int[] result = strategy.assign(D);
        assertEquals(1, result[0]);
        assertEquals(0, result[1]);
    }

    @Test
    public void assign_partialForbidden_bestValidAssigned() {
        double[][] D = {
            {Double.MAX_VALUE, 16.0, 25.0},
            {9.0, Double.MAX_VALUE, 36.0}
        };
        int[] result = strategy.assign(D);
        assertEquals("track 0 gets det 1 (16)", 1, result[0]);
        assertEquals("track 1 gets det 0 (9)", 0, result[1]);
    }

    // ------------------------------------------------------------------ large / stress

    @Test
    public void assign_5x5_squareDistanceMatrix_returnsValidPermutation() {
        double[][] D = {
            {10, 30, 15, 25, 20},
            {30, 10, 20, 15, 25},
            {15, 20, 10, 30, 25},
            {25, 15, 30, 10, 20},
            {20, 25, 25, 20, 10}
        };
        int[] result = strategy.assign(D);
        assertEquals(5, result.length);
        // Diagonal is the minimum for each row, so expect diagonal assignment
        for (int i = 0; i < 5; i++) {
            assertEquals("track " + i + " should be assigned to det " + i, i, result[i]);
        }
    }

    @Test
    public void assign_rectangularMoreTracks_returnLengthMatchesTracks() {
        double[][] D = {
            {1.0, 100.0},
            {100.0, 2.0},
            {50.0, 50.0},  // third track — one detection will be unmatched
        };
        int[] result = strategy.assign(D);
        assertEquals(3, result.length);
        // One of the three tracks must be unmatched
        long unmatched = Arrays.stream(result).filter(r -> r == -1).count();
        assertEquals(1, unmatched);
    }

    @Test
    public void assign_symmetricCosts_bothAlgorithmsAgree() {
        // When costs are symmetric and each track has a unique nearest detection,
        // greedy and Hungarian should agree.
        double[][] D = {
            {1.0, 200.0, 300.0},
            {200.0, 4.0, 300.0},
            {300.0, 300.0, 9.0}
        };
        AssignmentStrategy greedy = new GreedyAssignmentStrategy();
        int[] greedyResult = greedy.assign(D);
        int[] hungResult = strategy.assign(D);
        assertArrayEquals("Greedy and Hungarian should agree on diagonal assignment",
                greedyResult, hungResult);
    }
}
