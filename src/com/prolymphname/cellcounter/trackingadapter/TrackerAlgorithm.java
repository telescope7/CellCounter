package com.prolymphname.cellcounter.trackingadapter;

/**
 * Identifies the assignment algorithm used by the centroid tracker to match
 * detected bounding boxes to existing tracks in each frame.
 */
public enum TrackerAlgorithm {

    /**
     * Greedy nearest-neighbour assignment. Tracks are sorted by their minimum
     * valid distance to any detection; each track is greedily assigned to its
     * closest unmatched detection. O(t·d) per frame. Default algorithm.
     */
    GREEDY("Greedy (default)"),

    /**
     * Hungarian / Kuhn-Munkres algorithm. Solves the globally optimal minimum-
     * cost assignment in O(n³) where n = max(tracks, detections). Produces
     * better results when tracks are densely packed and distances are similar.
     */
    HUNGARIAN("Hungarian (optimal)");

    private final String displayName;

    TrackerAlgorithm(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }

    /** Parse from a properties-file token; case-insensitive, falls back to GREEDY. */
    public static TrackerAlgorithm fromString(String value) {
        if (value == null) return GREEDY;
        return switch (value.trim().toUpperCase()) {
            case "HUNGARIAN" -> HUNGARIAN;
            default -> GREEDY;
        };
    }
}
