package com.prolymphname.cellcounter.trackingadapter;

/**
 * Strategy for solving the track-to-detection assignment problem each frame.
 *
 * <p>The caller builds a distance matrix {@code D[i][j]} where:
 * <ul>
 *   <li>row {@code i} represents an active track,</li>
 *   <li>column {@code j} represents a new detection,</li>
 *   <li>{@code D[i][j]} is the squared-Euclidean distance between track {@code i} and
 *       detection {@code j}, or {@link Double#MAX_VALUE} when the pair is forbidden
 *       (violates movement constraints or exceeds the maximum association distance).</li>
 * </ul>
 *
 * <p>Implementations must return an {@code int[]} of length equal to the number of tracks
 * where {@code result[i]} is the detection index assigned to track {@code i}, or {@code -1}
 * if track {@code i} is left unmatched this frame.  Each detection index may appear at most
 * once in the result.
 */
public interface AssignmentStrategy {

    /**
     * Assigns detections to tracks.
     *
     * @param distanceMatrix {@code D[track][detection]} — squared Euclidean distances;
     *                       {@link Double#MAX_VALUE} marks forbidden pairs
     * @return array of length {@code distanceMatrix.length} where entry {@code i} is the
     *         zero-based detection index assigned to track {@code i}, or {@code -1}
     */
    int[] assign(double[][] distanceMatrix);
}
