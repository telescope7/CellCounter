package com.prolymphname.cellcounter.export;

import com.prolymphname.cellcounter.AnalysisLogic;
import com.prolymphname.cellcounter.trackingadapter.TrackingAdapter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

public class AnalysisExportService {
    public void saveAnalysisCsv(File file, TrackingAdapter trackingAdapter, ExportMetadata metadata) throws IOException {
        AnalysisLogic.CentroidTracker tracker = trackingAdapter.getCellTracker();
        if (tracker == null) {
            throw new IllegalStateException("Analysis data (tracker) is not available.");
        }

        ExportMetadata safeMetadata = metadata == null ? ExportMetadata.EMPTY : metadata;
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println(
                    "CellType,Substrate,FlowCondition,CellID,FirstSeenFrame,FirstSeenTime(s),CrossingFrame,CrossingTime(s),"
                            + "TotalDistance,DistanceToCross,DistanceAfterCross,"
                            + "AvgFrameDistance,MedianFrameDistance,FramesTracked,FramesMissed,Speed(pixels/sec)");

            for (Map.Entry<Integer, AnalysisLogic.Track> entry : getAllTracks(tracker).entrySet()) {
                int cellID = entry.getKey();
                AnalysisLogic.Track track = entry.getValue();
                if (track.history.isEmpty()) {
                    continue;
                }

                Map<String, Object> metrics = trackingAdapter.computeMetricsForTrack(track);
                int firstFrame = track.startFrame;
                double firstTime = track.startTime;
                int crossFrame = -1;
                double crossTime = -1.0;

                pw.printf("%s,%s,%s,%d,%d,%.2f,%d,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%d,%d,%.2f%n",
                        safeMetadata.getCellType(),
                        safeMetadata.getSubstrate(),
                        safeMetadata.getFlowCondition(),
                        cellID,
                        firstFrame,
                        firstTime,
                        crossFrame,
                        crossTime,
                        metricAsDouble(metrics, "TotalDistance"),
                        metricAsDouble(metrics, "DistanceToCross"),
                        metricAsDouble(metrics, "DistanceAfterCross"),
                        metricAsDouble(metrics, "AvgFrameDistance"),
                        metricAsDouble(metrics, "MedianFrameDistance"),
                        metricAsInt(metrics, "FramesTracked"),
                        metricAsInt(metrics, "FramesMissed"),
                        metricAsDouble(metrics, "Speed"));
            }
        }
    }

    public void saveFootprintCsv(File file, TrackingAdapter trackingAdapter, ExportMetadata metadata) throws IOException {
        AnalysisLogic.CentroidTracker tracker = trackingAdapter.getCellTracker();
        if (tracker == null) {
            throw new IllegalStateException("Footprint data (tracker) is not available.");
        }

        ExportMetadata safeMetadata = metadata == null ? ExportMetadata.EMPTY : metadata;
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("CellType,Substrate,FlowCondition,CellID,Frame,UL_X,UL_Y,LR_X,LR_Y");
            for (Map.Entry<Integer, AnalysisLogic.Track> entry : getAllTracks(tracker).entrySet()) {
                int cellID = entry.getKey();
                AnalysisLogic.Track track = entry.getValue();
                for (AnalysisLogic.HistoryItem item : track.history) {
                    pw.printf("%s,%s,%s,%d,%d,%d,%d,%d,%d%n",
                            safeMetadata.getCellType(),
                            safeMetadata.getSubstrate(),
                            safeMetadata.getFlowCondition(),
                            cellID,
                            item.frame,
                            (int) item.UL.x,
                            (int) item.UL.y,
                            (int) item.LR.x,
                            (int) item.LR.y);
                }
            }
        }
    }

    private static Map<Integer, AnalysisLogic.Track> getAllTracks(AnalysisLogic.CentroidTracker tracker) {
        Map<Integer, AnalysisLogic.Track> allTracks = new HashMap<>();
        allTracks.putAll(tracker.objects);
        allTracks.putAll(tracker.completeTracks);
        return allTracks;
    }

    private static double metricAsDouble(Map<String, Object> metrics, String key) {
        Object value = metrics.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return 0.0;
    }

    private static int metricAsInt(Map<String, Object> metrics, String key) {
        Object value = metrics.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }
}
