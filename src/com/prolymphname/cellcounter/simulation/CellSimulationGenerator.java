package com.prolymphname.cellcounter.simulation;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoWriter;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class CellSimulationGenerator {
    private static final double TRACK_LIMIT_FRACTION = 0.8;

    public interface ProgressListener {
        void onProgress(int currentFrame, int totalFrames);
    }

    public record SimulationResult(
            Path videoPath,
            Path legacyCsvPath,
            Path eventsCsvPath,
            Path trajectoryCsvPath,
            Path manifestJsonPath,
            int totalFrames,
            int totalCells) {
    }

    public SimulationResult generate(CellSimulationParameters rawParams, ProgressListener progressListener) throws IOException {
        CellSimulationParameters params = rawParams.normalized();
        Files.createDirectories(params.outputDirectory());

        int totalFrames = params.fps() * params.lengthSeconds();
        double trackLimit = TRACK_LIMIT_FRACTION * params.width();

        double meanTime = (params.delaySeconds() + params.lengthSeconds()) / 2.0;
        double stdDevTime = Math.max(0.001, (params.lengthSeconds() - params.delaySeconds()) / 6.0);

        Random random = new Random(params.randomSeed());
        int[] entryFrames = new int[params.cellCount()];
        int[] yPositions = new int[params.cellCount()];
        double[] entryTimes = new double[params.cellCount()];
        double[] velocityScales = new double[params.cellCount()];
        double[] velocities = new double[params.cellCount()];

        for (int i = 0; i < params.cellCount(); i++) {
            double sampledTime = meanTime + random.nextGaussian() * stdDevTime;
            double clippedTime = clamp(sampledTime, params.delaySeconds(), params.lengthSeconds());
            entryTimes[i] = clippedTime;
            entryFrames[i] = (int) (clippedTime * params.fps());
            yPositions[i] = random.nextInt(params.height());
            velocityScales[i] = 0.8 + random.nextDouble() * 0.4;
            velocities[i] = velocityScales[i] * params.baseVelocityPxPerFrame();
        }

        Path outputDir = params.outputDirectory();
        Path preferredVideo = outputDir.resolve(params.outputBaseName() + ".mp4");
        VideoWriter writer = new VideoWriter(
                preferredVideo.toString(),
                VideoWriter.fourcc('m', 'p', '4', 'v'),
                params.fps(),
                new Size(params.width(), params.height()));
        Path actualVideoPath = preferredVideo;

        if (!writer.isOpened()) {
            preferredVideo = outputDir.resolve(params.outputBaseName() + ".avi");
            writer = new VideoWriter(
                    preferredVideo.toString(),
                    VideoWriter.fourcc('M', 'J', 'P', 'G'),
                    params.fps(),
                    new Size(params.width(), params.height()));
            actualVideoPath = preferredVideo;
            if (!writer.isOpened()) {
                throw new IOException("Unable to open video writer for MP4 or AVI output.");
            }
        }

        Path legacyCsv = outputDir.resolve(params.outputBaseName() + ".csv");
        Path eventsCsv = outputDir.resolve(params.outputBaseName() + "_events.csv");
        Path trajectoryCsv = outputDir.resolve(params.outputBaseName() + "_trajectory.csv");
        Path manifestJson = outputDir.resolve(params.outputBaseName() + "_manifest.json");

        Map<Integer, ActiveCell> activeCells = new LinkedHashMap<>();
        Map<Integer, MutableEvent> events = new LinkedHashMap<>();

        try (PrintWriter trajectoryWriter = new PrintWriter(Files.newBufferedWriter(trajectoryCsv, StandardCharsets.UTF_8))) {
            trajectoryWriter.println("frame,time_sec,cell_id,x_px,y_px,cell_radius_px,crossed_80pct");

            for (int frameNum = 0; frameNum < totalFrames; frameNum++) {
                Mat frame = Mat.zeros(params.height(), params.width(), CvType.CV_8UC3);

                Iterator<Map.Entry<Integer, ActiveCell>> iterator = activeCells.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<Integer, ActiveCell> entry = iterator.next();
                    int cellId = entry.getKey();
                    ActiveCell active = entry.getValue();

                    double xPos = (frameNum - active.startFrame) * active.velocity;
                    if (xPos >= params.width()) {
                        MutableEvent event = events.get(cellId);
                        if (event != null) {
                            event.exitFrame = frameNum;
                            event.exitTimeSec = frameNum / (double) params.fps();
                        }
                        iterator.remove();
                        continue;
                    }

                    if (!active.crossed80 && xPos >= trackLimit) {
                        MutableEvent event = events.get(cellId);
                        if (event != null) {
                            event.cross80Frame = frameNum;
                            event.cross80TimeSec = frameNum / (double) params.fps();
                        }
                        active.crossed80 = true;
                    }

                    int driftMax = (int) (0.03 * params.cellRadiusPx());
                    int yDrift = driftMax > 0 ? random.nextInt(driftMax * 2 + 1) - driftMax : 0;
                    int drawY = clamp(active.baseY + yDrift, 0, params.height() - 1);

                    Imgproc.circle(frame, new Point((int) xPos, drawY), params.cellRadiusPx(), new Scalar(200, 200, 200), -1);
                    trajectoryWriter.printf(Locale.US, "%d,%.6f,%d,%.3f,%d,%d,%s%n",
                            frameNum,
                            frameNum / (double) params.fps(),
                            cellId,
                            xPos,
                            drawY,
                            params.cellRadiusPx(),
                            active.crossed80 ? "true" : "false");
                }

                for (int i = 0; i < params.cellCount(); i++) {
                    if (entryFrames[i] == frameNum) {
                        ActiveCell active = new ActiveCell(frameNum, yPositions[i], velocities[i]);
                        activeCells.put(i, active);
                        events.put(i, new MutableEvent(
                                i,
                                entryTimes[i],
                                frameNum,
                                yPositions[i],
                                velocities[i],
                                velocityScales[i]));
                    }
                }

                writer.write(frame);
                frame.release();

                if (progressListener != null) {
                    progressListener.onProgress(frameNum + 1, totalFrames);
                }
            }
        } finally {
            writer.release();
        }

        writeLegacyCsv(legacyCsv, events);
        writeEventsCsv(eventsCsv, events);
        writeManifest(manifestJson, params, actualVideoPath, legacyCsv, eventsCsv, trajectoryCsv, totalFrames);

        return new SimulationResult(actualVideoPath, legacyCsv, eventsCsv, trajectoryCsv, manifestJson, totalFrames, params.cellCount());
    }

    private void writeLegacyCsv(Path csvPath, Map<Integer, MutableEvent> events) throws IOException {
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(csvPath, StandardCharsets.UTF_8))) {
            writer.println("cell_id,insertion_time_sec,insertion_frame,last_visible_time_sec,last_visible_frame");
            for (MutableEvent event : events.values()) {
                writer.printf(Locale.US, "%d,%.6f,%d,%s,%s%n",
                        event.cellId,
                        event.insertionTimeSec,
                        event.insertionFrame,
                        nullableDouble(event.cross80TimeSec),
                        nullableInteger(event.cross80Frame));
            }
        }
    }

    private void writeEventsCsv(Path csvPath, Map<Integer, MutableEvent> events) throws IOException {
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(csvPath, StandardCharsets.UTF_8))) {
            writer.println("cell_id,insertion_time_sec,insertion_frame,cross_80_time_sec,cross_80_frame,exit_time_sec,exit_frame,entry_y_px,true_velocity_px_per_frame,velocity_scale");
            for (MutableEvent event : events.values()) {
                writer.printf(Locale.US, "%d,%.6f,%d,%s,%s,%s,%s,%d,%.6f,%.6f%n",
                        event.cellId,
                        event.insertionTimeSec,
                        event.insertionFrame,
                        nullableDouble(event.cross80TimeSec),
                        nullableInteger(event.cross80Frame),
                        nullableDouble(event.exitTimeSec),
                        nullableInteger(event.exitFrame),
                        event.entryYPx,
                        event.trueVelocityPxPerFrame,
                        event.velocityScale);
            }
        }
    }

    private void writeManifest(
            Path manifestPath,
            CellSimulationParameters params,
            Path videoPath,
            Path legacyCsv,
            Path eventsCsv,
            Path trajectoryCsv,
            int totalFrames) throws IOException {
        String json = "{\n"
                + "  \"schema\": \"cellcounter.simulation.groundtruth.v1\",\n"
                + "  \"generated_at_utc\": \"" + escapeJson(Instant.now().toString()) + "\",\n"
                + "  \"video_path\": \"" + escapeJson(videoPath.toAbsolutePath().toString()) + "\",\n"
                + "  \"legacy_csv_path\": \"" + escapeJson(legacyCsv.toAbsolutePath().toString()) + "\",\n"
                + "  \"events_csv_path\": \"" + escapeJson(eventsCsv.toAbsolutePath().toString()) + "\",\n"
                + "  \"trajectory_csv_path\": \"" + escapeJson(trajectoryCsv.toAbsolutePath().toString()) + "\",\n"
                + "  \"purpose\": \"Ground-truth generation for future automated comparison against CellCounter tracking outputs.\",\n"
                + "  \"parameters\": {\n"
                + "    \"width\": " + params.width() + ",\n"
                + "    \"height\": " + params.height() + ",\n"
                + "    \"cell_count\": " + params.cellCount() + ",\n"
                + "    \"delay_seconds\": " + params.delaySeconds() + ",\n"
                + "    \"fps\": " + params.fps() + ",\n"
                + "    \"length_seconds\": " + params.lengthSeconds() + ",\n"
                + "    \"cell_radius_px\": " + params.cellRadiusPx() + ",\n"
                + "    \"base_velocity_px_per_frame\": " + formatDouble(params.baseVelocityPxPerFrame()) + ",\n"
                + "    \"random_seed\": " + params.randomSeed() + ",\n"
                + "    \"track_limit_fraction\": " + formatDouble(TRACK_LIMIT_FRACTION) + ",\n"
                + "    \"total_frames\": " + totalFrames + "\n"
                + "  }\n"
                + "}\n";

        Files.writeString(manifestPath, json, StandardCharsets.UTF_8);
    }

    private static String formatDouble(double value) {
        return String.format(Locale.US, "%.6f", value);
    }

    private static String nullableDouble(Double value) {
        if (value == null) {
            return "";
        }
        return String.format(Locale.US, "%.6f", value);
    }

    private static String nullableInteger(Integer value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static final class ActiveCell {
        private final int startFrame;
        private final int baseY;
        private final double velocity;
        private boolean crossed80;

        private ActiveCell(int startFrame, int baseY, double velocity) {
            this.startFrame = startFrame;
            this.baseY = baseY;
            this.velocity = velocity;
            this.crossed80 = false;
        }
    }

    private static final class MutableEvent {
        private final int cellId;
        private final double insertionTimeSec;
        private final int insertionFrame;
        private final int entryYPx;
        private final double trueVelocityPxPerFrame;
        private final double velocityScale;
        private Double cross80TimeSec;
        private Integer cross80Frame;
        private Double exitTimeSec;
        private Integer exitFrame;

        private MutableEvent(
                int cellId,
                double insertionTimeSec,
                int insertionFrame,
                int entryYPx,
                double trueVelocityPxPerFrame,
                double velocityScale) {
            this.cellId = cellId;
            this.insertionTimeSec = insertionTimeSec;
            this.insertionFrame = insertionFrame;
            this.entryYPx = entryYPx;
            this.trueVelocityPxPerFrame = trueVelocityPxPerFrame;
            this.velocityScale = velocityScale;
        }
    }
}
