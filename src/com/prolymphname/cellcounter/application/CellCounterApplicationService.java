package com.prolymphname.cellcounter.application;

import com.prolymphname.cellcounter.export.AnalysisExportService;
import com.prolymphname.cellcounter.export.ExportMetadata;
import com.prolymphname.cellcounter.trackingadapter.AnalysisLogicTrackingAdapter;
import com.prolymphname.cellcounter.trackingadapter.TrackingConfiguration;
import com.prolymphname.cellcounter.trackingadapter.TrackingAdapter;
import com.prolymphname.cellcounter.ui.TuningPreviewFrames;
import org.opencv.core.Mat;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class CellCounterApplicationService {
    private final TrackingAdapter trackingAdapter;
    private final AnalysisExportService exportService;

    public CellCounterApplicationService() {
        this(new AnalysisLogicTrackingAdapter(), new AnalysisExportService());
    }

    public CellCounterApplicationService(TrackingAdapter trackingAdapter, AnalysisExportService exportService) {
        this.trackingAdapter = trackingAdapter;
        this.exportService = exportService;
    }

    public boolean initializeVideo(String videoPath) {
        return trackingAdapter.initializeVideo(videoPath);
    }

    public void resetAnalysisForCurrentVideo() {
        trackingAdapter.resetAnalysisForCurrentVideo();
    }

    public Mat processNextFrameForGUI() {
        return trackingAdapter.processNextFrameForGUI();
    }

    public Mat processNextFrameForAnalysis() {
        return trackingAdapter.processNextFrameForAnalysis();
    }

    public Mat seekToFrameForGUI(int targetFrameIndex) {
        return trackingAdapter.seekToFrameForGUI(targetFrameIndex);
    }

    public void releaseVideo() {
        trackingAdapter.releaseVideo();
    }

    public boolean isCaptureActive() {
        return trackingAdapter.isCaptureActive();
    }

    public boolean isVideoSuccessfullyInitialized() {
        return trackingAdapter.isVideoSuccessfullyInitialized();
    }

    public List<Double> getTrackStartTimes() {
        return trackingAdapter.getTrackStartTimes();
    }

    public List<Double> getSpeeds() {
        return trackingAdapter.getSpeeds();
    }

    public double getFps() {
        return trackingAdapter.getFps();
    }

    public int getFrameCount() {
        return trackingAdapter.getFrameCount();
    }

    public int getCurrentFrameNumber() {
        return trackingAdapter.getCurrentFrameNumber();
    }

    public Mat getLastProcessedFrame() {
        return trackingAdapter.getLastProcessedFrame();
    }

    public Mat getLastForegroundDisplayFrame() {
        return trackingAdapter.getLastForegroundDisplayFrame();
    }

    public void setMirrorTrackingInRawEnabled(boolean show) {
        trackingAdapter.setMirrorTrackingInRawEnabled(show);
    }

    public boolean isMirrorTrackingInRawEnabled() {
        return trackingAdapter.isMirrorTrackingInRawEnabled();
    }

    public void setReferenceFrameForDiff(Mat frame) {
        trackingAdapter.setReferenceFrameForDiff(frame);
    }

    public TrackingConfiguration getTrackingConfiguration() {
        return trackingAdapter.getTrackingConfiguration();
    }

    public void setTrackingConfiguration(TrackingConfiguration trackingConfiguration) {
        trackingAdapter.setTrackingConfiguration(trackingConfiguration);
    }

    public TuningPreviewFrames previewCurrentFramePairForTuning(TrackingConfiguration trackingConfiguration) {
        Mat rawPreview = trackingAdapter.previewRawCurrentFrameForTuning(trackingConfiguration);
        Mat foregroundPreview = null;
        try {
            foregroundPreview = trackingAdapter.previewForegroundCurrentFrameForTuning(trackingConfiguration);
            return new TuningPreviewFrames(rawPreview, foregroundPreview);
        } catch (RuntimeException ex) {
            if (rawPreview != null) {
                rawPreview.release();
            }
            if (foregroundPreview != null) {
                foregroundPreview.release();
            }
            throw ex;
        }
    }

    public void saveAnalysisCsv(File file, ExportMetadata metadata) throws IOException {
        exportService.saveAnalysisCsv(file, trackingAdapter, metadata);
    }

    public void saveFootprintCsv(File file, ExportMetadata metadata) throws IOException {
        exportService.saveFootprintCsv(file, trackingAdapter, metadata);
    }
}
