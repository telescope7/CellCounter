package com.prolymphname.cellcounter.trackingadapter;

import com.prolymphname.cellcounter.AnalysisLogic;
import org.opencv.core.Mat;

import java.util.List;
import java.util.Map;

public class AnalysisLogicTrackingAdapter implements TrackingAdapter {
    private final AnalysisLogic analysisLogic;

    public AnalysisLogicTrackingAdapter() {
        this(new AnalysisLogic());
    }

    public AnalysisLogicTrackingAdapter(AnalysisLogic analysisLogic) {
        this.analysisLogic = analysisLogic;
    }

    @Override
    public boolean initializeVideo(String videoPath) {
        return analysisLogic.initializeVideo(videoPath);
    }

    @Override
    public void resetAnalysisForCurrentVideo() {
        analysisLogic.resetAnalysisForCurrentVideo();
    }

    @Override
    public Mat processNextFrameForGUI() {
        return analysisLogic.processNextFrameForGUI();
    }

    @Override
    public Mat processNextFrameForAnalysis() {
        return analysisLogic.processNextFrameForAnalysis();
    }

    @Override
    public void releaseVideo() {
        analysisLogic.releaseVideo();
    }

    @Override
    public boolean isCaptureActive() {
        return analysisLogic.isCaptureActive();
    }

    @Override
    public boolean isVideoSuccessfullyInitialized() {
        return analysisLogic.isVideoSuccessfullyInitialized();
    }

    @Override
    public List<Double> getTrackStartTimes() {
        return analysisLogic.getTrackStartTimes();
    }

    @Override
    public List<Double> getSpeeds() {
        return analysisLogic.getSpeeds();
    }

    @Override
    public double getFps() {
        return analysisLogic.getFps();
    }

    @Override
    public int getFrameCount() {
        return analysisLogic.getFrameCount();
    }

    @Override
    public int getCurrentFrameNumber() {
        return analysisLogic.getCurrentFrameNumber();
    }

    @Override
    public Mat getLastProcessedFrame() {
        return analysisLogic.getLastProcessedFrame();
    }

    @Override
    public void setDisplayMOG2Foreground(boolean show) {
        analysisLogic.setDisplayMOG2Foreground(show);
    }

    @Override
    public void setReferenceFrameForDiff(Mat frame) {
        analysisLogic.setReferenceFrameForDiff(frame);
    }

    @Override
    public AnalysisLogic.CentroidTracker getCellTracker() {
        return analysisLogic.getCellTracker();
    }

    @Override
    public Map<String, Object> computeMetricsForTrack(AnalysisLogic.Track track) {
        return analysisLogic.computeMetricsForTrack(track);
    }
}
