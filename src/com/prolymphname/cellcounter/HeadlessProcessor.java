package com.prolymphname.cellcounter;

import com.prolymphname.cellcounter.application.CellCounterApplicationService;
import com.prolymphname.cellcounter.export.ExportMetadata;
import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;

import java.io.File;
import java.io.IOException;

public class HeadlessProcessor {

    private final CellCounterApplicationService appService;

    public HeadlessProcessor() {
        this(new CellCounterApplicationService());
    }

    public HeadlessProcessor(CellCounterApplicationService appService) {
        this.appService = appService;
    }

    public void run(String inputVideoPath, String outputPrefix, String cellType, String substrate, String flow) {
        System.out.println("Headless mode: input=" + inputVideoPath + "  prefix=" + outputPrefix);

        if (!appService.initializeVideo(inputVideoPath)) {
            System.err.println("ERROR: Cannot open or initialize video: " + inputVideoPath);
            return;
        }

        VideoCapture capForFirst = new VideoCapture(inputVideoPath);
        if (capForFirst.isOpened()) {
            Mat firstRawFrame = new Mat();
            if (capForFirst.read(firstRawFrame)) {
                appService.setReferenceFrameForDiff(firstRawFrame.clone());
            }
            firstRawFrame.release();
            capForFirst.release();
        } else {
            System.err.println("Could not open video for reference frame: " + inputVideoPath);
            capForFirst.release();
        }

        System.out.println("Processing frames...");
        Mat processedFrame;
        while ((processedFrame = appService.processNextFrameForAnalysis()) != null) {
            if (appService.getCurrentFrameNumber() % 100 == 0) {
                System.out.println("Processed frame: " + appService.getCurrentFrameNumber());
            }
            processedFrame.release();
        }

        System.out.println("Processing complete. Saving data...");
        ExportMetadata metadata = new ExportMetadata(cellType, substrate, flow);
        File analysisFile = new File(outputPrefix + "_analysis.csv");
        File footprintFile = new File(outputPrefix + "_footprint.csv");

        try {
            appService.saveAnalysisCsv(analysisFile, metadata);
            System.out.println("Wrote analysis CSV: " + analysisFile.getAbsolutePath());
        } catch (IOException | IllegalStateException ex) {
            System.err.println("Error writing analysis CSV: " + ex.getMessage());
        }

        try {
            appService.saveFootprintCsv(footprintFile, metadata);
            System.out.println("Wrote footprint CSV: " + footprintFile.getAbsolutePath());
        } catch (IOException | IllegalStateException ex) {
            System.err.println("Error writing footprint CSV: " + ex.getMessage());
        }

        appService.releaseVideo();
        System.out.println("Headless processing done.");
    }
}
