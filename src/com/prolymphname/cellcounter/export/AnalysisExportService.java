package com.prolymphname.cellcounter.export;

import com.prolymphname.cellcounter.analysis.TrackMetricsCalculator;
import com.prolymphname.cellcounter.trackingadapter.TrackMetrics;
import com.prolymphname.cellcounter.trackingadapter.TrackedCell;
import com.prolymphname.cellcounter.trackingadapter.TrackedCellHistoryEntry;
import com.prolymphname.cellcounter.trackingadapter.TrackingAdapter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

public class AnalysisExportService {
    private final TrackMetricsCalculator trackMetricsCalculator;

    public AnalysisExportService() {
        this(new TrackMetricsCalculator());
    }

    AnalysisExportService(TrackMetricsCalculator trackMetricsCalculator) {
        this.trackMetricsCalculator = trackMetricsCalculator;
    }

    public void saveAnalysisCsv(File file, TrackingAdapter trackingAdapter, ExportMetadata metadata) throws IOException {
        List<TrackedCell> trackedCells = trackingAdapter.getTrackedCells();
        ExportMetadata safeMetadata = metadata == null ? ExportMetadata.EMPTY : metadata;
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println(
                    "CellType,Substrate,FlowCondition,CellID,FirstSeenFrame,FirstSeenTime(s),CrossingFrame,CrossingTime(s),"
                            + "TotalDistance,DistanceToCross,DistanceAfterCross,"
                            + "AvgFrameDistance,MedianFrameDistance,FramesTracked,FramesMissed,Speed(pixels/sec)");

            for (TrackedCell trackedCell : trackedCells) {
                if (!trackedCell.hasHistory()) {
                    continue;
                }

                TrackMetrics metrics = trackMetricsCalculator.calculate(trackedCell, trackingAdapter.getFps());
                int firstFrame = trackedCell.startFrame();
                double firstTime = trackedCell.startTime();
                int crossFrame = -1;
                double crossTime = -1.0;

                pw.printf("%s,%s,%s,%d,%d,%.2f,%d,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%d,%d,%.2f%n",
                        safeMetadata.getCellType(),
                        safeMetadata.getSubstrate(),
                        safeMetadata.getFlowCondition(),
                        trackedCell.cellId(),
                        firstFrame,
                        firstTime,
                        crossFrame,
                        crossTime,
                        metrics.totalDistance(),
                        metrics.distanceToCross(),
                        metrics.distanceAfterCross(),
                        metrics.avgFrameDistance(),
                        metrics.medianFrameDistance(),
                        metrics.framesTracked(),
                        metrics.framesMissed(),
                        metrics.speed());
            }
        }
    }

    public void saveFootprintCsv(File file, TrackingAdapter trackingAdapter, ExportMetadata metadata) throws IOException {
        List<TrackedCell> trackedCells = trackingAdapter.getTrackedCells();
        ExportMetadata safeMetadata = metadata == null ? ExportMetadata.EMPTY : metadata;
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("CellType,Substrate,FlowCondition,CellID,Frame,UL_X,UL_Y,LR_X,LR_Y");
            for (TrackedCell trackedCell : trackedCells) {
                for (TrackedCellHistoryEntry item : trackedCell.history()) {
                    pw.printf("%s,%s,%s,%d,%d,%d,%d,%d,%d%n",
                            safeMetadata.getCellType(),
                            safeMetadata.getSubstrate(),
                            safeMetadata.getFlowCondition(),
                            trackedCell.cellId(),
                            item.frame(),
                            item.upperLeftX(),
                            item.upperLeftY(),
                            item.lowerRightX(),
                            item.lowerRightY());
                }
            }
        }
    }
}
