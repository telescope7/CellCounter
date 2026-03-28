package com.prolymphname.cellcounter.trackingadapter;

import com.prolymphname.cellcounter.AnalysisLogic;
import com.prolymphname.cellcounter.ui.TuningPreviewFrames;
import org.opencv.core.Mat;

import java.util.List;

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
    public Mat seekToFrameForGUI(int targetFrameIndex) {
        return analysisLogic.seekToFrameForGUI(targetFrameIndex);
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
    public Mat getLastForegroundDisplayFrame() {
        return analysisLogic.getLastForegroundDisplayFrame();
    }

    @Override
    public void setMirrorTrackingInRawEnabled(boolean show) {
        analysisLogic.setMirrorTrackingInRawEnabled(show);
    }

    @Override
    public boolean isMirrorTrackingInRawEnabled() {
        return analysisLogic.isMirrorTrackingInRawEnabled();
    }

    @Override
    public void setReferenceFrameForDiff(Mat frame) {
        analysisLogic.setReferenceFrameForDiff(frame);
    }

    @Override
    public TrackingConfiguration getTrackingConfiguration() {
        return analysisLogic.getTrackingConfiguration();
    }

    @Override
    public void setTrackingConfiguration(TrackingConfiguration trackingConfiguration) {
        analysisLogic.setTrackingConfiguration(trackingConfiguration);
    }

    @Override
    public TuningPreviewFrames previewCurrentFramePairForTuning(TrackingConfiguration trackingConfiguration) {
        return analysisLogic.previewCurrentFramePairForTuning(trackingConfiguration);
    }

    @Override
    public List<TrackedCell> getTrackedCells() {
        return analysisLogic.getTrackedCellsSnapshot();
    }

    @Override
    public List<TrackStatusSnapshot> getCurrentTrackStatuses() {
        return analysisLogic.getCurrentTrackStatusesSnapshot();
    }
}
