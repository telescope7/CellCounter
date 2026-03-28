package com.prolymphname.cellcounter.application;

import com.prolymphname.cellcounter.export.AnalysisExportService;
import com.prolymphname.cellcounter.trackingadapter.TrackStatusSnapshot;
import com.prolymphname.cellcounter.trackingadapter.TrackedCell;
import com.prolymphname.cellcounter.trackingadapter.TrackingAdapter;
import com.prolymphname.cellcounter.trackingadapter.TrackingConfiguration;
import com.prolymphname.cellcounter.ui.TuningPreviewFrames;
import org.junit.Test;
import org.opencv.core.Mat;

import java.util.List;

import static org.junit.Assert.*;

public class CellCounterApplicationServiceTest {

    @Test
    public void setTrackingConfiguration_allowsSessionConfigurationBeforeVideoLoad() {
        StubTrackingAdapter adapter = new StubTrackingAdapter();
        CellCounterApplicationService service = new CellCounterApplicationService(adapter, new AnalysisExportService());
        TrackingConfiguration configuration = TrackingConfiguration.defaults();

        service.setTrackingConfiguration(configuration);

        assertSame(configuration, adapter.trackingConfiguration);
        assertSame(configuration, service.getTrackingConfiguration());
        assertFalse(service.isVideoSuccessfullyInitialized());
    }

    @Test
    public void previewCurrentFramePairForTuning_returnsEmptyPairWhenNoVideoPreviewExists() {
        StubTrackingAdapter adapter = new StubTrackingAdapter();
        CellCounterApplicationService service = new CellCounterApplicationService(adapter, new AnalysisExportService());

        try (TuningPreviewFrames frames = service.previewCurrentFramePairForTuning(TrackingConfiguration.defaults())) {
            assertNotNull(frames);
            assertNull(frames.rawFrame());
            assertNull(frames.foregroundFrame());
        }
    }

    @Test
    public void previewCurrentFramePairForTuning_combinesRawAndForegroundPreviewCalls() {
        StubTrackingAdapter adapter = new StubTrackingAdapter();
        CellCounterApplicationService service = new CellCounterApplicationService(adapter, new AnalysisExportService());

        try (TuningPreviewFrames frames = service.previewCurrentFramePairForTuning(TrackingConfiguration.defaults())) {
            assertNull(frames.rawFrame());
            assertNull(frames.foregroundFrame());
            assertEquals(1, adapter.previewPairCallCount);
        }
    }

    @Test
    public void mirrorTrackingFlag_delegatesToAdapter() {
        StubTrackingAdapter adapter = new StubTrackingAdapter();
        CellCounterApplicationService service = new CellCounterApplicationService(adapter, new AnalysisExportService());

        service.setMirrorTrackingInRawEnabled(true);

        assertTrue(adapter.mirrorTrackingInRawEnabled);
        assertTrue(service.isMirrorTrackingInRawEnabled());
    }

    private static final class StubTrackingAdapter implements TrackingAdapter {
        private TrackingConfiguration trackingConfiguration = TrackingConfiguration.defaults();
        private boolean mirrorTrackingInRawEnabled = false;
        private int previewPairCallCount = 0;

        @Override
        public boolean initializeVideo(String videoPath) {
            return false;
        }

        @Override
        public void resetAnalysisForCurrentVideo() {
        }

        @Override
        public Mat processNextFrameForGUI() {
            return null;
        }

        @Override
        public Mat processNextFrameForAnalysis() {
            return null;
        }

        @Override
        public Mat seekToFrameForGUI(int targetFrameIndex) {
            return null;
        }

        @Override
        public void releaseVideo() {
        }

        @Override
        public boolean isCaptureActive() {
            return false;
        }

        @Override
        public boolean isVideoSuccessfullyInitialized() {
            return false;
        }

        @Override
        public List<Double> getTrackStartTimes() {
            return List.of();
        }

        @Override
        public List<Double> getSpeeds() {
            return List.of();
        }

        @Override
        public double getFps() {
            return 30.0;
        }

        @Override
        public int getFrameCount() {
            return 0;
        }

        @Override
        public int getCurrentFrameNumber() {
            return 0;
        }

        @Override
        public Mat getLastProcessedFrame() {
            return null;
        }

        @Override
        public Mat getLastForegroundDisplayFrame() {
            return null;
        }

        @Override
        public void setMirrorTrackingInRawEnabled(boolean show) {
            mirrorTrackingInRawEnabled = show;
        }

        @Override
        public boolean isMirrorTrackingInRawEnabled() {
            return mirrorTrackingInRawEnabled;
        }

        @Override
        public void setReferenceFrameForDiff(Mat frame) {
        }

        @Override
        public TrackingConfiguration getTrackingConfiguration() {
            return trackingConfiguration;
        }

        @Override
        public void setTrackingConfiguration(TrackingConfiguration trackingConfiguration) {
            this.trackingConfiguration = trackingConfiguration;
        }

        @Override
        public TuningPreviewFrames previewCurrentFramePairForTuning(TrackingConfiguration trackingConfiguration) {
            previewPairCallCount++;
            return new TuningPreviewFrames(null, null);
        }

        @Override
        public List<TrackedCell> getTrackedCells() {
            return List.of();
        }

        @Override
        public List<TrackStatusSnapshot> getCurrentTrackStatuses() {
            return List.of();
        }
    }
}
