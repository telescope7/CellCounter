package com.prolymphname.cellcounter.evaluation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GroundTruthEvaluator {
    private static final double EPS = 1e-12;

    public EvaluationResult evaluate(
            Path truthEventsCsv,
            Path predictedAnalysisCsv,
            double matchWindowSeconds,
            Double fpsOverride) throws IOException {

        List<TruthEvent> truthEvents = readTruthEvents(truthEventsCsv);
        if (truthEvents.isEmpty()) {
            throw new IllegalArgumentException("No valid rows were found in truth events CSV: " + truthEventsCsv);
        }

        double fps = resolveFps(truthEvents, fpsOverride);
        List<TruthEventNormalized> normalizedTruth = new ArrayList<>(truthEvents.size());
        List<Double> truthTimes = new ArrayList<>(truthEvents.size());
        List<Double> truthVelocitiesPxPerSec = new ArrayList<>(truthEvents.size());
        for (TruthEvent event : truthEvents) {
            double velocityPxPerSec = event.trueVelocityPxPerFrame() * fps;
            normalizedTruth.add(new TruthEventNormalized(event.insertionTimeSec(), velocityPxPerSec));
            truthTimes.add(event.insertionTimeSec());
            truthVelocitiesPxPerSec.add(velocityPxPerSec);
        }

        List<PredictedTrack> predictedTracks = readPredictedTracks(predictedAnalysisCsv);
        if (predictedTracks.isEmpty()) {
            return EvaluationResult.empty(
                    truthEventsCsv,
                    predictedAnalysisCsv,
                    truthEvents.size(),
                    matchWindowSeconds,
                    fps,
                    truthTimes,
                    truthVelocitiesPxPerSec);
        }

        List<Double> predictedTimes = new ArrayList<>(predictedTracks.size());
        List<Double> predictedVelocities = new ArrayList<>(predictedTracks.size());
        for (PredictedTrack track : predictedTracks) {
            predictedTimes.add(track.firstSeenTimeSec());
            predictedVelocities.add(track.speedPxPerSec());
        }

        List<MatchedPair> matches = matchByTimeWindow(normalizedTruth, predictedTracks, matchWindowSeconds);
        int tp = matches.size();
        int fp = predictedTracks.size() - tp;
        int fn = normalizedTruth.size() - tp;

        double precision = tp + fp > 0 ? (double) tp / (double) (tp + fp) : 0.0;
        double recall = tp + fn > 0 ? (double) tp / (double) (tp + fn) : 0.0;
        double f1 = (precision + recall) > 0 ? (2.0 * precision * recall) / (precision + recall) : 0.0;

        double velocityAbsErrorSum = 0.0;
        for (MatchedPair match : matches) {
            velocityAbsErrorSum += Math.abs(match.predictedVelocityPxPerSec() - match.truthVelocityPxPerSec());
        }
        double maeVelocity = matches.isEmpty() ? Double.NaN : velocityAbsErrorSum / matches.size();

        double w1Time = wasserstein1(truthTimes, predictedTimes);
        double w1Velocity = wasserstein1(truthVelocitiesPxPerSec, predictedVelocities);

        return new EvaluationResult(
                truthEventsCsv.toAbsolutePath(),
                predictedAnalysisCsv.toAbsolutePath(),
                normalizedTruth.size(),
                predictedTracks.size(),
                tp,
                fp,
                fn,
                precision,
                recall,
                f1,
                w1Time,
                w1Velocity,
                maeVelocity,
                matchWindowSeconds,
                fps,
                matches.size());
    }

    private static List<TruthEvent> readTruthEvents(Path csvPath) throws IOException {
        List<String> lines = Files.readAllLines(csvPath);
        if (lines.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> header = splitCsvLine(lines.get(0));
        Map<String, Integer> columns = indexColumns(header);

        int insertionTimeIdx = requireColumn(columns, "insertiontimesec");
        int velocityIdx = requireColumn(columns, "truevelocitypxperframe");

        Integer insertionFrameIdx = columns.get("insertionframe");
        Integer crossTimeIdx = firstPresent(columns, "cross80timesec");
        Integer crossFrameIdx = firstPresent(columns, "cross80frame");
        Integer exitTimeIdx = columns.get("exittimesec");
        Integer exitFrameIdx = columns.get("exitframe");

        List<TruthEvent> events = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) {
                continue;
            }
            List<String> cells = splitCsvLine(line);
            Double insertionTime = parseDoubleCell(cells, insertionTimeIdx);
            Double velocityPxPerFrame = parseDoubleCell(cells, velocityIdx);
            if (insertionTime == null || velocityPxPerFrame == null) {
                continue;
            }

            Integer insertionFrame = parseIntCell(cells, insertionFrameIdx);
            Integer crossFrame = parseIntCell(cells, crossFrameIdx);
            Double crossTime = parseDoubleCell(cells, crossTimeIdx);
            Integer exitFrame = parseIntCell(cells, exitFrameIdx);
            Double exitTime = parseDoubleCell(cells, exitTimeIdx);

            events.add(new TruthEvent(
                    insertionTime,
                    velocityPxPerFrame,
                    insertionFrame,
                    crossTime,
                    crossFrame,
                    exitTime,
                    exitFrame));
        }
        return events;
    }

    private static List<PredictedTrack> readPredictedTracks(Path csvPath) throws IOException {
        List<String> lines = Files.readAllLines(csvPath);
        if (lines.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> header = splitCsvLine(lines.get(0));
        Map<String, Integer> columns = indexColumns(header);

        int firstSeenTimeIdx = firstPresentRequired(columns, "firstseentimes", "firstseentime");
        int speedIdx = firstPresentRequired(columns, "speedpixelssec", "speed");

        List<PredictedTrack> tracks = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) {
                continue;
            }
            List<String> cells = splitCsvLine(line);
            Double firstSeenTime = parseDoubleCell(cells, firstSeenTimeIdx);
            Double speed = parseDoubleCell(cells, speedIdx);
            if (firstSeenTime == null || speed == null) {
                continue;
            }
            tracks.add(new PredictedTrack(firstSeenTime, speed));
        }
        return tracks;
    }

    private static List<MatchedPair> matchByTimeWindow(
            List<TruthEventNormalized> truthEvents,
            List<PredictedTrack> predictedTracks,
            double matchWindowSeconds) {

        List<IndexedTruth> truth = new ArrayList<>(truthEvents.size());
        for (int i = 0; i < truthEvents.size(); i++) {
            truth.add(new IndexedTruth(i, truthEvents.get(i)));
        }
        truth.sort(Comparator.comparingDouble(t -> t.event().insertionTimeSec()));

        List<IndexedPrediction> predicted = new ArrayList<>(predictedTracks.size());
        for (int i = 0; i < predictedTracks.size(); i++) {
            predicted.add(new IndexedPrediction(i, predictedTracks.get(i)));
        }
        predicted.sort(Comparator.comparingDouble(p -> p.track().firstSeenTimeSec()));

        List<MatchCandidate> candidates = new ArrayList<>();
        for (IndexedTruth truthItem : truth) {
            for (IndexedPrediction predictionItem : predicted) {
                double delta = Math.abs(truthItem.event().insertionTimeSec() - predictionItem.track().firstSeenTimeSec());
                if (delta <= matchWindowSeconds) {
                    candidates.add(new MatchCandidate(truthItem, predictionItem, delta));
                }
            }
        }

        candidates.sort(Comparator.comparingDouble(MatchCandidate::deltaSeconds));
        boolean[] truthMatched = new boolean[truthEvents.size()];
        boolean[] predictionMatched = new boolean[predictedTracks.size()];

        List<MatchedPair> matches = new ArrayList<>();
        for (MatchCandidate candidate : candidates) {
            int truthIdx = candidate.truth().index();
            int predIdx = candidate.prediction().index();
            if (truthMatched[truthIdx] || predictionMatched[predIdx]) {
                continue;
            }

            truthMatched[truthIdx] = true;
            predictionMatched[predIdx] = true;
            matches.add(new MatchedPair(
                    candidate.truth().event().insertionTimeSec(),
                    candidate.prediction().track().firstSeenTimeSec(),
                    candidate.truth().event().velocityPxPerSec(),
                    candidate.prediction().track().speedPxPerSec(),
                    candidate.deltaSeconds()));
        }
        return matches;
    }

    private static double resolveFps(List<TruthEvent> events, Double fpsOverride) {
        List<Double> samples = new ArrayList<>();
        for (TruthEvent event : events) {
            addFpsSample(samples, event.insertionFrame(), event.insertionTimeSec(), event.crossFrame(), event.crossTimeSec());
            addFpsSample(samples, event.crossFrame(), event.crossTimeSec(), event.exitFrame(), event.exitTimeSec());
            addFpsSample(samples, event.insertionFrame(), event.insertionTimeSec(), event.exitFrame(), event.exitTimeSec());
        }

        if (!samples.isEmpty()) {
            Collections.sort(samples);
            int mid = samples.size() / 2;
            if (samples.size() % 2 == 0) {
                return (samples.get(mid - 1) + samples.get(mid)) / 2.0;
            }
            return samples.get(mid);
        }

        if (fpsOverride != null && fpsOverride > 0.0) {
            return fpsOverride;
        }

        throw new IllegalArgumentException(
                "Unable to infer FPS from truth events CSV. Provide --fps=<value> explicitly.");
    }

    private static void addFpsSample(
            List<Double> samples,
            Integer frameA,
            Double timeA,
            Integer frameB,
            Double timeB) {
        if (frameA == null || timeA == null || frameB == null || timeB == null) {
            return;
        }
        int frameDelta = frameB - frameA;
        double timeDelta = timeB - timeA;
        if (frameDelta <= 0 || timeDelta <= EPS) {
            return;
        }
        samples.add((double) frameDelta / timeDelta);
    }

    private static double wasserstein1(List<Double> a, List<Double> b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 0.0;
        }
        if (a.isEmpty() || b.isEmpty()) {
            return Double.NaN;
        }

        List<Double> x = new ArrayList<>(a);
        List<Double> y = new ArrayList<>(b);
        Collections.sort(x);
        Collections.sort(y);

        int n = x.size();
        int m = y.size();
        int i = 0;
        int j = 0;
        double cdfX = 0.0;
        double cdfY = 0.0;
        double prev = Math.min(x.get(0), y.get(0));
        double area = 0.0;

        while (i < n || j < m) {
            double next;
            if (i < n && j < m) {
                next = Math.min(x.get(i), y.get(j));
            } else if (i < n) {
                next = x.get(i);
            } else {
                next = y.get(j);
            }

            area += Math.abs(cdfX - cdfY) * (next - prev);

            while (i < n && x.get(i) <= next + EPS) {
                i++;
            }
            while (j < m && y.get(j) <= next + EPS) {
                j++;
            }

            cdfX = (double) i / (double) n;
            cdfY = (double) j / (double) m;
            prev = next;
        }

        return area;
    }

    private static int requireColumn(Map<String, Integer> columns, String key) {
        Integer idx = columns.get(key);
        if (idx == null) {
            throw new IllegalArgumentException("Missing required CSV column: " + key);
        }
        return idx;
    }

    private static int firstPresentRequired(Map<String, Integer> columns, String... keys) {
        Integer idx = firstPresent(columns, keys);
        if (idx == null) {
            throw new IllegalArgumentException("Missing required CSV column. Expected one of: " + String.join(", ", keys));
        }
        return idx;
    }

    private static Integer firstPresent(Map<String, Integer> columns, String... keys) {
        for (String key : keys) {
            Integer idx = columns.get(key);
            if (idx != null) {
                return idx;
            }
        }
        return null;
    }

    private static Map<String, Integer> indexColumns(List<String> headerCells) {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < headerCells.size(); i++) {
            index.put(normalizeKey(headerCells.get(i)), i);
        }
        return index;
    }

    private static String normalizeKey(String key) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                builder.append(Character.toLowerCase(c));
            }
        }
        return builder.toString();
    }

    private static List<String> splitCsvLine(String line) {
        String[] parts = line.split(",", -1);
        List<String> cells = new ArrayList<>(parts.length);
        for (String part : parts) {
            cells.add(part.trim());
        }
        return cells;
    }

    private static Double parseDoubleCell(List<String> cells, Integer index) {
        if (index == null || index < 0 || index >= cells.size()) {
            return null;
        }
        String value = cells.get(index).trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Integer parseIntCell(List<String> cells, Integer index) {
        if (index == null || index < 0 || index >= cells.size()) {
            return null;
        }
        String value = cells.get(index).trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public record EvaluationResult(
            Path truthEventsCsv,
            Path predictedAnalysisCsv,
            int truthCount,
            int predictedCount,
            int truePositives,
            int falsePositives,
            int falseNegatives,
            double precision,
            double recall,
            double f1,
            double wassersteinTimeSec,
            double wassersteinVelocityPxPerSec,
            double maeVelocityPxPerSec,
            double matchWindowSeconds,
            double inferredFps,
            int matchedVelocityCount) {

        private static EvaluationResult empty(
                Path truthEventsCsv,
                Path predictedAnalysisCsv,
                int truthCount,
                double matchWindowSeconds,
                double inferredFps,
                List<Double> truthTimes,
                List<Double> truthVelocitiesPxPerSec) {
            return new EvaluationResult(
                    truthEventsCsv.toAbsolutePath(),
                    predictedAnalysisCsv.toAbsolutePath(),
                    truthCount,
                    0,
                    0,
                    0,
                    truthCount,
                    0.0,
                    0.0,
                    0.0,
                    wasserstein1(truthTimes, Collections.emptyList()),
                    wasserstein1(truthVelocitiesPxPerSec, Collections.emptyList()),
                    Double.NaN,
                    matchWindowSeconds,
                    inferredFps,
                    0);
        }
    }

    private record TruthEvent(
            double insertionTimeSec,
            double trueVelocityPxPerFrame,
            Integer insertionFrame,
            Double crossTimeSec,
            Integer crossFrame,
            Double exitTimeSec,
            Integer exitFrame) {
    }

    private record TruthEventNormalized(double insertionTimeSec, double velocityPxPerSec) {
    }

    private record PredictedTrack(double firstSeenTimeSec, double speedPxPerSec) {
    }

    private record IndexedTruth(int index, TruthEventNormalized event) {
    }

    private record IndexedPrediction(int index, PredictedTrack track) {
    }

    private record MatchCandidate(IndexedTruth truth, IndexedPrediction prediction, double deltaSeconds) {
    }

    private record MatchedPair(
            double truthInsertionTimeSec,
            double predictedFirstSeenTimeSec,
            double truthVelocityPxPerSec,
            double predictedVelocityPxPerSec,
            double absoluteTimeDeltaSec) {
    }
}
