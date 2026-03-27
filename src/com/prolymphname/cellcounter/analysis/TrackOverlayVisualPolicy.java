package com.prolymphname.cellcounter.analysis;

import org.opencv.core.Point;
import org.opencv.core.Rect;

public class TrackOverlayVisualPolicy {

    public TrackVisualState resolveState(boolean newTrack, int missedFrames) {
        if (missedFrames > 0) {
            return TrackVisualState.MISSED;
        }
        return newTrack ? TrackVisualState.NEW : TrackVisualState.STABLE;
    }

    public boolean isOcclusionRisk(
            Rect firstBox,
            Point firstCentroid,
            Rect secondBox,
            Point secondCentroid,
            double maxAssociationDistancePixels) {
        if (firstBox == null || secondBox == null || firstCentroid == null || secondCentroid == null) {
            return false;
        }

        Rect intersection = intersect(firstBox, secondBox);
        if (intersection != null && intersection.width > 0 && intersection.height > 0) {
            return true;
        }

        double centroidDistance = Math.hypot(firstCentroid.x - secondCentroid.x, firstCentroid.y - secondCentroid.y);
        double averageDiagonal = (Math.hypot(firstBox.width, firstBox.height)
                + Math.hypot(secondBox.width, secondBox.height)) / 2.0;
        double proximityThreshold = Math.max(
                10.0,
                Math.min(maxAssociationDistancePixels * 0.35, averageDiagonal * 0.9));
        return centroidDistance <= proximityThreshold;
    }

    private Rect intersect(Rect first, Rect second) {
        int left = Math.max(first.x, second.x);
        int top = Math.max(first.y, second.y);
        int right = Math.min(first.x + first.width, second.x + second.width);
        int bottom = Math.min(first.y + first.height, second.y + second.height);
        if (right <= left || bottom <= top) {
            return null;
        }
        return new Rect(left, top, right - left, bottom - top);
    }
}
