package com.prolymphname.cellcounter.trackingadapter;

import java.util.Arrays;

/**
 * Globally-optimal assignment strategy using the Hungarian (Kuhn-Munkres) algorithm.
 *
 * <p>Finds the minimum-cost perfect matching on an augmented cost matrix so that
 * tracks and detections can be left unmatched when no valid pairing exists.
 * Forbidden pairs (indicated by {@link Double#MAX_VALUE} in the distance matrix)
 * are never chosen when a valid alternative is available.
 *
 * <p>Complexity: O(n³) where n = max(numTracks, numDetections).
 */
public class HungarianAssignmentStrategy implements AssignmentStrategy {

    /**
     * Sentinel cost placed in the augmented matrix to represent a "real track
     * goes unmatched" slot. Must be larger than any valid distance but smaller
     * than FORBIDDEN so that unmatched is preferred over a forbidden pair.
     */
    private static final double SENTINEL_UNMATCHED = 1e14;

    /**
     * Sentinel replacing {@link Double#MAX_VALUE} in the cost matrix. Must be
     * larger than SENTINEL_UNMATCHED so forbidden pairs are avoided when any
     * valid or unmatched option exists.
     */
    private static final double SENTINEL_FORBIDDEN = 1e15;

    /**
     * Any assignment whose effective cost is below this value is treated as a
     * real (valid) match.  Set to half of SENTINEL_UNMATCHED.
     */
    private static final double VALID_THRESHOLD = SENTINEL_UNMATCHED / 2;

    @Override
    public int[] assign(double[][] distanceMatrix) {
        int m = distanceMatrix.length;
        int[] result = new int[m];
        Arrays.fill(result, -1);
        if (m == 0) return result;

        int numDetections = distanceMatrix[0].length;
        if (numDetections == 0) return result;

        // Augmented square cost matrix of size n = max(m, numDetections).
        // Extra rows (ghost tracks) represent "detection j is unmatched" — cost 0.
        // Extra cols (ghost detections) represent "track i is unmatched" — cost SENTINEL_UNMATCHED.
        int n = Math.max(m, numDetections);
        double[][] cost = new double[n][n];

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i < m && j < numDetections) {
                    double d = distanceMatrix[i][j];
                    cost[i][j] = (d == Double.MAX_VALUE) ? SENTINEL_FORBIDDEN : d;
                } else if (i < m) {
                    // real track i → ghost detection column: cost of being unmatched
                    cost[i][j] = SENTINEL_UNMATCHED;
                } else {
                    // ghost track row → any col: free (unmatched detection has no penalty)
                    cost[i][j] = 0.0;
                }
            }
        }

        int[] assignment = hungarianSquare(cost, n);

        for (int i = 0; i < m; i++) {
            int j = assignment[i];
            if (j < numDetections && cost[i][j] < VALID_THRESHOLD) {
                result[i] = j;
            }
        }
        return result;
    }

    /**
     * Solves the n×n linear sum assignment problem (minimisation) using the
     * successive-shortest-paths formulation with Johnson re-weighting.
     *
     * <p>Based on the classical O(n³) potential-based implementation.
     *
     * @param a n×n cost matrix (no {@link Double#MAX_VALUE} entries; use
     *          {@link #SENTINEL_FORBIDDEN} instead to avoid arithmetic overflow)
     * @param n matrix dimension
     * @return {@code assignment[i] = j} (0-indexed) for each row i
     */
    private static int[] hungarianSquare(double[][] a, int n) {
        // Use Double.MAX_VALUE/4 as internal INF to avoid overflow when
        // computing reduced costs (cost - u[i] - v[j]).
        final double INF = Double.MAX_VALUE / 4.0;

        // 1-indexed potentials; index 0 is a virtual "dummy" row/column.
        double[] u = new double[n + 1]; // row potentials
        double[] v = new double[n + 1]; // col potentials

        // p[j] = row (1-indexed) currently matched to column j; 0 means free.
        int[] p = new int[n + 1];
        // way[j] = the column we came from when we first improved dist[j].
        int[] way = new int[n + 1];

        for (int i = 1; i <= n; i++) {
            // Temporarily assign virtual row 0 to row i as starting point.
            p[0] = i;
            int j0 = 0;

            double[] dist = new double[n + 1];
            Arrays.fill(dist, INF);
            boolean[] used = new boolean[n + 1];

            // Dijkstra-like shortest augmenting path from row i.
            do {
                used[j0] = true;
                int i0 = p[j0];
                double delta = INF;
                int j1 = -1;

                for (int j = 1; j <= n; j++) {
                    if (!used[j]) {
                        double rawCost = (i0 >= 1 && i0 <= n) ? a[i0 - 1][j - 1] : INF;
                        // Clamp extreme values to avoid overflow in subtraction.
                        if (rawCost > INF) rawCost = INF;
                        double reducedCost = rawCost - u[i0] - v[j];
                        if (reducedCost < dist[j]) {
                            dist[j] = reducedCost;
                            way[j] = j0;
                        }
                        if (dist[j] < delta) {
                            delta = dist[j];
                            j1 = j;
                        }
                    }
                }

                if (j1 == -1 || delta >= INF) break;

                // Update potentials.
                for (int j = 0; j <= n; j++) {
                    if (used[j]) {
                        u[p[j]] += delta;
                        v[j] -= delta;
                    } else {
                        dist[j] -= delta;
                    }
                }
                j0 = j1;
            } while (p[j0] != 0);

            // Augment along the path.
            do {
                int j1 = way[j0];
                p[j0] = p[j1];
                j0 = j1;
            } while (j0 != 0);
        }

        // Convert p[] to 0-indexed result array.
        int[] result = new int[n];
        Arrays.fill(result, -1);
        for (int j = 1; j <= n; j++) {
            if (p[j] >= 1 && p[j] <= n) {
                result[p[j] - 1] = j - 1;
            }
        }
        return result;
    }
}
