package com.prolymphname.cellcounter.trackingadapter;

import org.opencv.core.Mat;

import java.util.List;

public interface TrackingAdapter {
    boolean initializeVideo(String videoPath);

    void resetAnalysisForCurrentVideo();

    Mat processNextFrameForGUI();

    Mat processNextFrameForAnalysis();

    Mat seekToFrameForGUI(int targetFrameIndex);

    void releaseVideo();

    boolean isCaptureActive();

    boolean isVideoSuccessfullyInitialized();

    List<Double> getTrackStartTimes();

    List<Double> getSpeeds();

    double getFps();

    int getFrameCount();

    int getCurrentFrameNumber();

    Mat getLastProcessedFrame();

    void setDisplayMOG2Foreground(boolean show);

    void setReferenceFrameForDiff(Mat frame);

    TrackingConfiguration getTrackingConfiguration();

    void setTrackingConfiguration(TrackingConfiguration trackingConfiguration);

    Mat previewCurrentFrameForTuning(TrackingConfiguration trackingConfiguration, boolean showMaskView);

    List<TrackedCell> getTrackedCells();
}
