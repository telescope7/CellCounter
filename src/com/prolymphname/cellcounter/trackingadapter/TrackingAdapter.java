package com.prolymphname.cellcounter.trackingadapter;

import com.prolymphname.cellcounter.AnalysisLogic;
import org.opencv.core.Mat;

import java.util.List;
import java.util.Map;

public interface TrackingAdapter {
    boolean initializeVideo(String videoPath);

    void resetAnalysisForCurrentVideo();

    Mat processNextFrameForGUI();

    Mat processNextFrameForAnalysis();

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

    AnalysisLogic.CentroidTracker getCellTracker();

    Map<String, Object> computeMetricsForTrack(AnalysisLogic.Track track);
}
