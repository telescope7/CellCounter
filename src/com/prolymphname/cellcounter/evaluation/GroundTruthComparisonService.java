package com.prolymphname.cellcounter.evaluation;

import com.prolymphname.cellcounter.application.CellCounterApplicationService;
import com.prolymphname.cellcounter.export.ExportMetadata;
import com.prolymphname.cellcounter.trackingadapter.TrackingConfiguration;
import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class GroundTruthComparisonService {
    private final Path videoPath;
    private final Path truthEventsCsv;
    private final double matchWindowSeconds;
    private final Double fpsOverride;
    private final Path workspaceDir;
    private final double candidateTimeoutSeconds;
    private final int maxFramesToProcess;
    private final GroundTruthEvaluator evaluator;

    public GroundTruthComparisonService(
            Path videoPath,
            Path truthEventsCsv,
            double matchWindowSeconds,
            Double fpsOverride,
            Path workspaceDir,
            double candidateTimeoutSeconds,
            int maxFramesToProcess) {
        this.videoPath = videoPath.toAbsolutePath();
        this.truthEventsCsv = truthEventsCsv.toAbsolutePath();
        this.matchWindowSeconds = matchWindowSeconds;
        this.fpsOverride = fpsOverride;
        this.workspaceDir = workspaceDir.toAbsolutePath();
        this.candidateTimeoutSeconds = candidateTimeoutSeconds;
        this.maxFramesToProcess = maxFramesToProcess;
        this.evaluator = new GroundTruthEvaluator();
    }

    public GroundTruthEvaluator.EvaluationResult evaluate(TrackingConfiguration configuration) throws IOException {
        Files.createDirectories(workspaceDir);
        Path analysisCsv = Files.createTempFile(workspaceDir, "ga_eval_", "_analysis.csv");
        try {
            runHeadlessAnalysis(configuration, analysisCsv);
            return evaluator.evaluate(truthEventsCsv, analysisCsv, matchWindowSeconds, fpsOverride);
        } finally {
            Files.deleteIfExists(analysisCsv);
        }
    }

    private void runHeadlessAnalysis(TrackingConfiguration configuration, Path analysisCsv) throws IOException {
        CellCounterApplicationService appService = new CellCounterApplicationService();
        appService.setTrackingConfiguration(configuration);

        String inputVideoPath = videoPath.toString();
        if (!appService.initializeVideo(inputVideoPath)) {
            appService.releaseVideo();
            throw new IllegalStateException("Cannot open or initialize video: " + inputVideoPath);
        }

        try {
            VideoCapture capForFirst = new VideoCapture(inputVideoPath);
            try {
                if (capForFirst.isOpened()) {
                    Mat firstRawFrame = new Mat();
                    try {
                        if (capForFirst.read(firstRawFrame)) {
                            appService.setReferenceFrameForDiff(firstRawFrame.clone());
                        }
                    } finally {
                        firstRawFrame.release();
                    }
                }
            } finally {
                capForFirst.release();
            }

            Mat processedFrame;
            int processedFrames = 0;
            long startedNanos = System.nanoTime();
            while ((processedFrame = appService.processNextFrameForAnalysis()) != null) {
                processedFrame.release();
                processedFrames++;

                if (maxFramesToProcess > 0 && processedFrames >= maxFramesToProcess) {
                    break;
                }

                if (candidateTimeoutSeconds > 0.0) {
                    double elapsedSec = (System.nanoTime() - startedNanos) / 1_000_000_000.0;
                    if (elapsedSec > candidateTimeoutSeconds) {
                        throw new IllegalStateException(String.format(
                                "Candidate evaluation timed out after %.2f sec at frame %d",
                                elapsedSec,
                                processedFrames));
                    }
                }
            }

            appService.saveAnalysisCsv(analysisCsv.toFile(), ExportMetadata.EMPTY);
        } finally {
            appService.releaseVideo();
        }
    }
}
