package com.prolymphname.cellcounter.trackingadapter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Greedy nearest-neighbour assignment strategy.
 *
 * <p>Tracks are sorted by their minimum finite distance to any detection; each
 * track is then greedily assigned to its closest unmatched detection that is
 * within the association threshold. Runs in O(t·d) per frame.  This is the
 * default algorithm and matches the original CellCounter behaviour.
 */
public class GreedyAssignmentStrategy implements AssignmentStrategy {

    @Override
    public int[] assign(double[][] distanceMatrix) {
        int numTracks = distanceMatrix.length;
        int[] result = new int[numTracks];
        Arrays.fill(result, -1);

        if (numTracks == 0) return result;
        int numDetections = distanceMatrix[0].length;
        if (numDetections == 0) return result;

        // Sort track indices by their minimum valid distance (ascending)
        List<Integer> sortedTrackIndices = new ArrayList<>(numTracks);
        for (int i = 0; i < numTracks; i++) sortedTrackIndices.add(i);

        sortedTrackIndices.sort((a, b) -> {
            double minA = Arrays.stream(distanceMatrix[a]).min().orElse(Double.MAX_VALUE);
            double minB = Arrays.stream(distanceMatrix[b]).min().orElse(Double.MAX_VALUE);
            return Double.compare(minA, minB);
        });

        Set<Integer> usedDetections = new HashSet<>();

        for (int trackIdx : sortedTrackIndices) {
            double bestDist = Double.MAX_VALUE;
            int bestDetIdx = -1;

            for (int j = 0; j < numDetections; j++) {
                if (!usedDetections.contains(j) && distanceMatrix[trackIdx][j] < bestDist) {
                    bestDist = distanceMatrix[trackIdx][j];
                    bestDetIdx = j;
                }
            }

            if (bestDetIdx != -1) {
                result[trackIdx] = bestDetIdx;
                usedDetections.add(bestDetIdx);
            }
        }

        return result;
    }
}
