package com.prolymphname.cellcounter.export;

import com.prolymphname.cellcounter.trackingadapter.TrackedCell;
import com.prolymphname.cellcounter.trackingadapter.TrackedCellHistoryEntry;
import com.prolymphname.cellcounter.trackingadapter.TrackingAdapter;
import com.prolymphname.cellcounter.trackingadapter.TrackingConfiguration;
import org.junit.Test;
import org.opencv.core.Mat;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AnalysisExportServiceTest {
    private final AnalysisExportService exportService = new AnalysisExportService();

    @Test
    public void saveAnalysisCsv_writesHeaderAndMetricsFromTrackedCells() throws Exception {
        File output = File.createTempFile("analysis-export", ".csv");
        output.deleteOnExit();

        exportService.saveAnalysisCsv(
                output,
                new StubTrackingAdapter(sampleTrackedCells(), 2.0),
                new ExportMetadata("Cells", "Glass", "LowFlow"));

        List<String> lines = Files.readAllLines(output.toPath());
        assertEquals(2, lines.size());
        assertTrue(lines.get(0).startsWith("CellType,Substrate,FlowCondition"));
        assertTrue(lines.get(1).contains("Cells,Glass,LowFlow,101,2,0.50,-1,-1.00,7.00"));
        assertTrue(lines.get(1).endsWith(",3,1,4.67"));
    }

    @Test
    public void saveFootprintCsv_writesOneLinePerHistoryEntry() throws Exception {
        File output = File.createTempFile("footprint-export", ".csv");
        output.deleteOnExit();

        exportService.saveFootprintCsv(
                output,
                new StubTrackingAdapter(sampleTrackedCells(), 2.0),
                ExportMetadata.EMPTY);

        List<String> lines = Files.readAllLines(output.toPath());
        assertEquals(4, lines.size());
        assertEquals("CellType,Substrate,FlowCondition,CellID,Frame,UL_X,UL_Y,LR_X,LR_Y", lines.get(0));
        assertEquals(",,,101,2,0,0,2,2", lines.get(1));
        assertEquals(",,,101,3,3,0,5,2", lines.get(2));
        assertEquals(",,,101,4,3,4,5,6", lines.get(3));
    }

    private List<TrackedCell> sampleTrackedCells() {
        return List.of(new TrackedCell(
                101,
                2,
                0.5,
                1,
                List.of(
                        new TrackedCellHistoryEntry(2, 0, 0, 2, 2),
                        new TrackedCellHistoryEntry(3, 3, 0, 5, 2),
                        new TrackedCellHistoryEntry(4, 3, 4, 5, 6))));
    }

    private static class StubTrackingAdapter implements TrackingAdapter {
        private final List<TrackedCell> trackedCells;
        private final double fps;

        private StubTrackingAdapter(List<TrackedCell> trackedCells, double fps) {
            this.trackedCells = trackedCells;
            this.fps = fps;
        }

        @Override
        public boolean initializeVideo(String videoPath) {
            throw unsupported();
        }

        @Override
        public void resetAnalysisForCurrentVideo() {
            throw unsupported();
        }

        @Override
        public Mat processNextFrameForGUI() {
            throw unsupported();
        }

        @Override
        public Mat processNextFrameForAnalysis() {
            throw unsupported();
        }

        @Override
        public Mat seekToFrameForGUI(int targetFrameIndex) {
            throw unsupported();
        }

        @Override
        public void releaseVideo() {
            throw unsupported();
        }

        @Override
        public boolean isCaptureActive() {
            throw unsupported();
        }

        @Override
        public boolean isVideoSuccessfullyInitialized() {
            throw unsupported();
        }

        @Override
        public List<Double> getTrackStartTimes() {
            throw unsupported();
        }

        @Override
        public List<Double> getSpeeds() {
            throw unsupported();
        }

        @Override
        public double getFps() {
            return fps;
        }

        @Override
        public int getFrameCount() {
            throw unsupported();
        }

        @Override
        public int getCurrentFrameNumber() {
            throw unsupported();
        }

        @Override
        public Mat getLastProcessedFrame() {
            throw unsupported();
        }

        @Override
        public void setDisplayMOG2Foreground(boolean show) {
            throw unsupported();
        }

        @Override
        public void setDisplayTrackTrails(boolean show) {
            throw unsupported();
        }

        @Override
        public boolean isDisplayTrackTrailsEnabled() {
            throw unsupported();
        }

        @Override
        public void setDisplayMatchRegion(boolean show) {
            throw unsupported();
        }

        @Override
        public boolean isDisplayMatchRegionEnabled() {
            throw unsupported();
        }

        @Override
        public void setReferenceFrameForDiff(Mat frame) {
            throw unsupported();
        }

        @Override
        public TrackingConfiguration getTrackingConfiguration() {
            throw unsupported();
        }

        @Override
        public void setTrackingConfiguration(TrackingConfiguration trackingConfiguration) {
            throw unsupported();
        }

        @Override
        public Mat previewCurrentFrameForTuning(TrackingConfiguration trackingConfiguration, boolean showMaskView) {
            throw unsupported();
        }

        @Override
        public List<TrackedCell> getTrackedCells() {
            return trackedCells;
        }

        private UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException("Not needed for export test.");
        }
    }
}
