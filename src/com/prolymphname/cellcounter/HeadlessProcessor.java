package com.prolymphname.cellcounter;

import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture; // For AnalysisLogic's autoDetectRotation


import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

public class HeadlessProcessor {
	
	String cellType = "";
	String substrate = "";
	String flow = "";

    private AnalysisLogic analysisLogic;

    public HeadlessProcessor() {
        this.analysisLogic = new AnalysisLogic();
    }

    public void run(String inputVideoPath, String outputPrefix, String cellType, String substrate, String flow) {
        System.out.println("Headless mode: input=" + inputVideoPath + "  prefix=" + outputPrefix);

        if (!analysisLogic.initializeVideo(inputVideoPath)) {
            System.err.println("ERROR: Cannot open or initialize video: " + inputVideoPath);
            return;
        }
        
        this.cellType = cellType;
        this.substrate = substrate;
        this.flow = flow;
        
        // Optional: Auto-detect rotation specifically for headless if desired
        // This requires passing the VideoCapture object to autoDetectRotation or re-opening.
        // For simplicity, using the one from initializeVideo or setting manually.
        // If using the original autoDetectRotation logic that needs direct cap access:
        VideoCapture tempCap = new VideoCapture(inputVideoPath);
        if(tempCap.isOpened()){
            // Must re-initialize or rewind and set first frame for diff if rotation changed after init
            analysisLogic.releaseVideo(); // Release old capture
            analysisLogic.initializeVideo(inputVideoPath); // Re-initialize with new rotation potentially
            tempCap.release();

            // Set reference frame for diff processing strategy if used by AnalysisLogic.processFrame
            Mat firstRawFrame = new Mat();
            VideoCapture capForFirst = new VideoCapture(inputVideoPath);
            if (capForFirst.read(firstRawFrame)) {
                 analysisLogic.setReferenceFrameForDiff(firstRawFrame.clone()); // analysisLogic will rotate it
            }
            firstRawFrame.release();
            capForFirst.release();
        } else {
            System.err.println("Could not open video: " );
            tempCap.release();
        }


        System.out.println("Processing frames...");
        Mat processedFrame;
        while ((processedFrame = analysisLogic.processNextFrameForAnalysis()) != null) { // Use analysis variant
            // Optional: Log progress
            if (analysisLogic.getCurrentFrameNumber() % 100 == 0) {
                System.out.println("Processed frame: " + analysisLogic.getCurrentFrameNumber());
            }
            if(processedFrame != null) processedFrame.release(); // Release if not used further
        }

        System.out.println("Processing complete. Saving data...");
        saveAnalysisData(outputPrefix + "_analysis.csv");
        saveFootprintData(outputPrefix + "_footprint.csv");

        analysisLogic.releaseVideo();
        System.out.println("Headless processing done.");
    }

    private void saveAnalysisData(String filename) {
        AnalysisLogic.CentroidTracker tracker = analysisLogic.getCellTracker();
        if (tracker == null) {
            System.err.println("Tracker not available for saving analysis data.");
            return;
        }

        try (PrintWriter pw = new PrintWriter(new FileWriter(filename))) {
            pw.println("CellType,Substrate,FlowCondition,CellID,FirstSeenFrame,FirstSeenTime(s),CrossingFrame,CrossingTime(s),"
                    + "TotalDistance,DistanceToCross,DistanceAfterCross,"
                    + "AvgFrameDistance,MedianFrameDistance,FramesTracked,FramesMissed,Speed(pixels/sec)");

            Map<Integer, AnalysisLogic.Track> allTracks = new HashMap<>();
            allTracks.putAll(tracker.objects); // Active tracks
            allTracks.putAll(tracker.completeTracks); // Deregistered tracks

            double fps = analysisLogic.getFps();

            for (Map.Entry<Integer, AnalysisLogic.Track> entry : allTracks.entrySet()) {
                int cellID = entry.getKey();
                AnalysisLogic.Track track = entry.getValue();
                if (track.history.isEmpty()) continue;

                Map<String, Object> metrics = analysisLogic.computeMetricsForTrack(track);

                int firstFrame = track.startFrame;
                double firstTime = track.startTime; // startTime is already in seconds
                
                int crossFrame = track.startFrame;
                double crossTime = -1.0;
                if (crossFrame > 0) {
                     crossTime = crossFrame / fps; // Calculate from frame and fps
                }


                pw.printf("%s,%s,%s,%d,%d,%.2f,%d,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%d,%d,%.2f%n",
                        cellType, substrate, flow,
                        cellID, firstFrame, firstTime, crossFrame > 0 ? crossFrame : -1, crossTime,
                        (Double) metrics.get("TotalDistance"), (Double) metrics.get("DistanceToCross"),
                        (Double) metrics.get("DistanceAfterCross"), (Double) metrics.get("AvgFrameDistance"),
                        (Double) metrics.get("MedianFrameDistance"), (Integer) metrics.get("FramesTracked"),
                        (Integer) metrics.get("FramesMissed"), (Double) metrics.get("Speed"));

            }
            System.out.println("Wrote analysis CSV: " + filename);
        } catch (IOException ex) {
            System.err.println("Error writing analysis CSV: " + ex.getMessage());
        }
    }

    private void saveFootprintData(String filename) {
        AnalysisLogic.CentroidTracker tracker = analysisLogic.getCellTracker();
        if (tracker == null) {
            System.err.println("Tracker not available for saving footprint data.");
            return;
        }
        try (PrintWriter pw = new PrintWriter(new FileWriter(filename))) {
            pw.println("CellType,Substrate,FlowCondition,CellID,Frame,UL_X,UL_Y,LR_X,LR_Y");
            Map<Integer, AnalysisLogic.Track> allTracks = new HashMap<>();
            allTracks.putAll(tracker.objects);
            allTracks.putAll(tracker.completeTracks);

            for (Map.Entry<Integer, AnalysisLogic.Track> entry : allTracks.entrySet()) {
                int cellID = entry.getKey();
                AnalysisLogic.Track track = entry.getValue();
                for (AnalysisLogic.HistoryItem item : track.history) {
                	pw.printf("%s,%s,%s,%d,%d,%d,%d,%d,%d%n",
                            cellType, substrate, flow,
                            cellID, item.frame,
                            (int) item.UL.x, (int) item.UL.y,
                            (int) item.LR.x, (int) item.LR.y);
                }
            }
            System.out.println("Wrote footprint CSV: " + filename);
        } catch (IOException ex) {
            System.err.println("Error writing footprint CSV: " + ex.getMessage());
        }
    }
}