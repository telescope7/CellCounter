package com.prolymphname.cellcounter.trackingadapter;

import com.prolymphname.cellcounter.ui.TuningPreviewFrames;
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

    Mat getLastForegroundDisplayFrame();

    void setMirrorTrackingInRawEnabled(boolean show);

    boolean isMirrorTrackingInRawEnabled();

    void setReferenceFrameForDiff(Mat frame);

    TrackingConfiguration getTrackingConfiguration();

    void setTrackingConfiguration(TrackingConfiguration trackingConfiguration);

    TuningPreviewFrames previewCurrentFramePairForTuning(TrackingConfiguration trackingConfiguration);

    List<TrackedCell> getTrackedCells();

    List<TrackStatusSnapshot> getCurrentTrackStatuses();
}
