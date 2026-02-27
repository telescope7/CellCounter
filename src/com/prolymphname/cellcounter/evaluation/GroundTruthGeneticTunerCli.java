package com.prolymphname.cellcounter.evaluation;

import com.prolymphname.cellcounter.OpenCvSupport;
import com.prolymphname.cellcounter.trackingadapter.TrackingConfiguration;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class GroundTruthGeneticTunerCli {
    private static final int MIN_MAX_FRAMES_DISAPPEARED = 1;
    private static final int MAX_MAX_FRAMES_DISAPPEARED = 10000;
    private static final double MIN_MIN_CONTOUR_AREA = 0.0;
    private static final double MAX_MIN_CONTOUR_AREA = 100000.0;
    private static final double MIN_MAX_RECT_CIRCUMFERENCE = 1.0;
    private static final double MAX_MAX_RECT_CIRCUMFERENCE = 100000.0;
    private static final double MIN_MAX_VERTICAL_DISPLACEMENT = 0.0;
    private static final double MAX_MAX_VERTICAL_DISPLACEMENT = 100000.0;
    private static final double MIN_MIN_HORIZONTAL_MOVEMENT = -100000.0;
    private static final double MAX_MIN_HORIZONTAL_MOVEMENT = 100000.0;
    private static final double MIN_MAX_ASSOCIATION_DISTANCE = 1.0;
    private static final double MAX_MAX_ASSOCIATION_DISTANCE = 100000.0;
    private static final int MIN_MOG2_HISTORY = 1;
    private static final int MAX_MOG2_HISTORY = 10000;
    private static final double MIN_MOG2_VAR_THRESHOLD = 0.01;
    private static final double MAX_MOG2_VAR_THRESHOLD = 1000.0;
    private static final int MIN_MORPH_KERNEL_SIZE = 1;
    private static final int MAX_MORPH_KERNEL_SIZE = 99;
    private static final int MIN_MORPH_OPEN_ITERATIONS = 0;
    private static final int MAX_MORPH_OPEN_ITERATIONS = 20;
    private static final int MIN_MORPH_DILATE_ITERATIONS = 0;
    private static final int MAX_MORPH_DILATE_ITERATIONS = 20;
    private static final double MIN_MASK_THRESHOLD = 0.0;
    private static final double MAX_MASK_THRESHOLD = 255.0;

    private static final int TOURNAMENT_SIZE = 3;
    private static final double DEFAULT_CROSSOVER_RATE = 0.75;
    private static final double DEFAULT_CANDIDATE_TIMEOUT_SECONDS = 45.0;
    private static final double MAX_RATIO_CAP = 3.0;
    private static final double EPS = 1e-9;

    private GroundTruthGeneticTunerCli() {
    }

    public static void main(String[] args) {
        if (args.length == 0 || containsHelp(args)) {
            printUsage();
            return;
        }

        try {
            OpenCvSupport.loadOpenCv();
            Options options = Options.parse(args);
            run(options);
        } catch (IllegalArgumentException ex) {
            System.err.println("Invalid arguments: " + ex.getMessage());
            printUsage();
            System.exit(2);
        } catch (Exception ex) {
            System.err.println("GA tuning failed: " + ex.getMessage());
            ex.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void run(Options options) throws IOException {
        Files.createDirectories(options.outputDir());
        Path evalWorkspace = options.outputDir().resolve("ga_eval_tmp");
        Files.createDirectories(evalWorkspace);
        int plannedMaxEvaluations = 1 + (options.generations() * options.populationSize());
        System.out.println("GA plan: generations=" + options.generations()
                + ", population=" + options.populationSize()
                + ", maxEvaluations~" + plannedMaxEvaluations
                + ", timeoutPerCandidateSec=" + formatDouble(options.candidateTimeoutSeconds())
                + ", maxFrames=" + options.maxFramesToProcess());

        TrackingConfiguration baselineConfig = TrackingConfigurationIO.loadFromProperties(options.trackingConfigPath());
        baselineConfig = snapWholeNumericGenes(baselineConfig);
        GroundTruthComparisonService comparisonService = new GroundTruthComparisonService(
                options.videoPath(),
                options.truthEventsCsv(),
                options.matchWindowSeconds(),
                options.fpsOverride(),
                evalWorkspace,
                options.candidateTimeoutSeconds(),
                options.maxFramesToProcess());

        System.out.println("Evaluating baseline configuration...");
        GroundTruthEvaluator.EvaluationResult baselineMetrics = comparisonService.evaluate(baselineConfig);
        FitnessScorer scorer = new FitnessScorer(baselineMetrics);
        Candidate baseline = Candidate.success(
                baselineConfig,
                baselineMetrics,
                scorer.score(baselineMetrics));

        Map<String, Candidate> cache = new HashMap<>();
        cache.put(TrackingConfigurationIO.canonicalKey(baselineConfig), baseline);

        Random random = new Random(options.seed());
        List<TrackingConfiguration> population = initializePopulation(options.populationSize(), baselineConfig, random);
        Candidate bestOverall = baseline;
        List<GenerationSummary> history = new ArrayList<>();

        for (int generation = 0; generation < options.generations(); generation++) {
            List<Candidate> evaluated = evaluatePopulation(
                    population,
                    comparisonService,
                    scorer,
                    cache,
                    generation + 1,
                    options.generations(),
                    options.candidateTimeoutSeconds());
            evaluated.sort(Comparator.comparingDouble(Candidate::fitness).reversed());

            Candidate bestInGeneration = evaluated.get(0);
            if (bestInGeneration.fitness() > bestOverall.fitness()) {
                bestOverall = bestInGeneration;
            }

            double avgScore = evaluated.stream().mapToDouble(Candidate::fitness).average().orElse(Double.NaN);
            history.add(new GenerationSummary(
                    generation + 1,
                    bestInGeneration.fitness(),
                    avgScore,
                    bestInGeneration.metrics().f1(),
                    bestInGeneration.metrics().wassersteinTimeSec(),
                    bestInGeneration.metrics().wassersteinVelocityPxPerSec(),
                    bestInGeneration.metrics().maeVelocityPxPerSec()));

            printGenerationSummary(generation + 1, options.generations(), bestInGeneration, avgScore);

            if (generation == options.generations() - 1) {
                break;
            }

            population = evolvePopulation(
                    evaluated,
                    options.populationSize(),
                    options.mutationRate(),
                    options.crossoverRate(),
                    options.eliteCount(),
                    random);
        }

        writeHistoryCsv(options.historyCsvPath(), history);
        writeFinalReport(options.reportPath(), baseline, bestOverall);
        TrackingConfigurationIO.saveToProperties(
                options.bestConfigPath(),
                bestOverall.configuration(),
                "GA optimized tracking configuration");

        printFinalSummary(baseline, bestOverall, options.bestConfigPath(), options.historyCsvPath(), options.reportPath());
    }

    private static List<TrackingConfiguration> initializePopulation(
            int populationSize,
            TrackingConfiguration baseline,
            Random random) {
        List<TrackingConfiguration> population = new ArrayList<>(populationSize);
        population.add(baseline);
        while (population.size() < populationSize) {
            boolean wideExploration = random.nextDouble() < 0.25;
            population.add(randomizeFromBaseline(baseline, random, wideExploration));
        }
        return population;
    }

    private static List<Candidate> evaluatePopulation(
            List<TrackingConfiguration> population,
            GroundTruthComparisonService comparisonService,
            FitnessScorer scorer,
            Map<String, Candidate> cache,
            int generationIndex,
            int totalGenerations,
            double hardTimeoutSeconds) {
        List<Candidate> evaluated = new ArrayList<>(population.size());
        for (int i = 0; i < population.size(); i++) {
            TrackingConfiguration configuration = population.get(i);
            String key = TrackingConfigurationIO.canonicalKey(configuration);
            Candidate existing = cache.get(key);
            int candidateIndex = i + 1;
            System.out.println("Generation " + generationIndex + "/" + totalGenerations
                    + " evaluating candidate " + candidateIndex + "/" + population.size() + "...");
            if (existing != null) {
                evaluated.add(existing);
                System.out.println("  reused cached score=" + String.format(Locale.US, "%.4f", existing.fitness()));
                continue;
            }

            Candidate candidate;
            long startedNanos = System.nanoTime();
            try {
                GroundTruthEvaluator.EvaluationResult result = evaluateWithHardTimeout(
                        comparisonService,
                        configuration,
                        hardTimeoutSeconds);
                double fitness = scorer.score(result);
                candidate = Candidate.success(configuration, result, fitness);
                double elapsedSec = (System.nanoTime() - startedNanos) / 1_000_000_000.0;
                System.out.println("  done in " + String.format(Locale.US, "%.2fs", elapsedSec)
                        + " score=" + String.format(Locale.US, "%.4f", fitness)
                        + " F1=" + String.format(Locale.US, "%.4f", result.f1()));
            } catch (Exception ex) {
                candidate = Candidate.failure(configuration, ex.getMessage());
                double elapsedSec = (System.nanoTime() - startedNanos) / 1_000_000_000.0;
                System.out.println("  failed in " + String.format(Locale.US, "%.2fs", elapsedSec)
                        + " reason=" + ex.getMessage());
            }

            cache.put(key, candidate);
            evaluated.add(candidate);
        }
        return evaluated;
    }

    private static GroundTruthEvaluator.EvaluationResult evaluateWithHardTimeout(
            GroundTruthComparisonService comparisonService,
            TrackingConfiguration configuration,
            double timeoutSeconds) throws Exception {
        if (timeoutSeconds <= 0.0) {
            return comparisonService.evaluate(configuration);
        }

        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ga-candidate-eval");
            t.setDaemon(true);
            return t;
        });

        try {
            Future<GroundTruthEvaluator.EvaluationResult> future =
                    executor.submit(() -> comparisonService.evaluate(configuration));
            try {
                return future.get((long) Math.ceil(timeoutSeconds), TimeUnit.SECONDS);
            } catch (TimeoutException ex) {
                future.cancel(true);
                throw new IllegalStateException(String.format(
                        Locale.US,
                        "Candidate hard-timeout after %.2f sec",
                        timeoutSeconds), ex);
            } catch (ExecutionException ex) {
                Throwable cause = ex.getCause();
                if (cause instanceof Exception exception) {
                    throw exception;
                }
                throw new IllegalStateException("Candidate evaluation failed", cause);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static List<TrackingConfiguration> evolvePopulation(
            List<Candidate> evaluated,
            int populationSize,
            double mutationRate,
            double crossoverRate,
            int eliteCount,
            Random random) {

        List<TrackingConfiguration> nextPopulation = new ArrayList<>(populationSize);
        int elites = Math.min(eliteCount, evaluated.size());
        for (int i = 0; i < elites; i++) {
            nextPopulation.add(evaluated.get(i).configuration());
        }

        while (nextPopulation.size() < populationSize) {
            Candidate parentA = tournamentSelect(evaluated, random);
            Candidate parentB = tournamentSelect(evaluated, random);

            TrackingConfiguration child;
            if (random.nextDouble() < crossoverRate) {
                child = crossover(parentA.configuration(), parentB.configuration(), random);
            } else {
                child = parentA.configuration();
            }

            child = mutate(child, mutationRate, random);
            nextPopulation.add(child);
        }

        return nextPopulation;
    }

    private static Candidate tournamentSelect(List<Candidate> evaluated, Random random) {
        Candidate best = null;
        for (int i = 0; i < TOURNAMENT_SIZE; i++) {
            Candidate candidate = evaluated.get(random.nextInt(evaluated.size()));
            if (best == null || candidate.fitness() > best.fitness()) {
                best = candidate;
            }
        }
        return best;
    }

    private static TrackingConfiguration randomizeFromBaseline(
            TrackingConfiguration baseline,
            Random random,
            boolean wideExploration) {
        int maxFramesDisappeared = wideExploration
                ? randomInt(random, MIN_MAX_FRAMES_DISAPPEARED, MAX_MAX_FRAMES_DISAPPEARED)
                : jitterInt(random, baseline.getMaxFramesDisappeared(), MIN_MAX_FRAMES_DISAPPEARED, MAX_MAX_FRAMES_DISAPPEARED, 0.45);

        double minContourArea = wideExploration
                ? randomDouble(random, MIN_MIN_CONTOUR_AREA, MAX_MIN_CONTOUR_AREA)
                : jitterDouble(random, baseline.getMinContourArea(), MIN_MIN_CONTOUR_AREA, MAX_MIN_CONTOUR_AREA, 0.45);

        double maxRectCircumference = wideExploration
                ? randomDouble(random, MIN_MAX_RECT_CIRCUMFERENCE, MAX_MAX_RECT_CIRCUMFERENCE)
                : jitterDouble(random, baseline.getMaxRectCircumference(),
                MIN_MAX_RECT_CIRCUMFERENCE, MAX_MAX_RECT_CIRCUMFERENCE, 0.45);

        double maxVerticalDisplacement = wideExploration
                ? randomDouble(random, MIN_MAX_VERTICAL_DISPLACEMENT, MAX_MAX_VERTICAL_DISPLACEMENT)
                : jitterDouble(random, baseline.getMaxVerticalDisplacementPixels(),
                MIN_MAX_VERTICAL_DISPLACEMENT, MAX_MAX_VERTICAL_DISPLACEMENT, 0.45);

        double minHorizontalMovement = wideExploration
                ? randomDouble(random, MIN_MIN_HORIZONTAL_MOVEMENT, MAX_MIN_HORIZONTAL_MOVEMENT)
                : jitterDouble(random, baseline.getMinHorizontalMovementPixels(),
                MIN_MIN_HORIZONTAL_MOVEMENT, MAX_MIN_HORIZONTAL_MOVEMENT, 0.45);

        double maxAssociationDistance = wideExploration
                ? randomDouble(random, MIN_MAX_ASSOCIATION_DISTANCE, MAX_MAX_ASSOCIATION_DISTANCE)
                : jitterDouble(random, baseline.getMaxAssociationDistancePixels(),
                MIN_MAX_ASSOCIATION_DISTANCE, MAX_MAX_ASSOCIATION_DISTANCE, 0.45);

        int mog2History = wideExploration
                ? randomInt(random, MIN_MOG2_HISTORY, MAX_MOG2_HISTORY)
                : jitterInt(random, baseline.getMog2HistoryFrames(), MIN_MOG2_HISTORY, MAX_MOG2_HISTORY, 0.45);

        double mog2VarThreshold = wideExploration
                ? randomDouble(random, MIN_MOG2_VAR_THRESHOLD, MAX_MOG2_VAR_THRESHOLD)
                : jitterDouble(random, baseline.getMog2VarThreshold(), MIN_MOG2_VAR_THRESHOLD, MAX_MOG2_VAR_THRESHOLD, 0.45);

        boolean detectShadows = random.nextDouble() < 0.7
                ? baseline.isMog2DetectShadows()
                : !baseline.isMog2DetectShadows();

        int morphologyKernelSize = wideExploration
                ? randomInt(random, MIN_MORPH_KERNEL_SIZE, MAX_MORPH_KERNEL_SIZE)
                : jitterInt(random, baseline.getMorphologyKernelSize(), MIN_MORPH_KERNEL_SIZE, MAX_MORPH_KERNEL_SIZE, 0.45);

        int morphologyOpenIterations = wideExploration
                ? randomInt(random, MIN_MORPH_OPEN_ITERATIONS, MAX_MORPH_OPEN_ITERATIONS)
                : jitterInt(random, baseline.getMorphologyOpenIterations(),
                MIN_MORPH_OPEN_ITERATIONS, MAX_MORPH_OPEN_ITERATIONS, 0.45);

        int morphologyDilateIterations = wideExploration
                ? randomInt(random, MIN_MORPH_DILATE_ITERATIONS, MAX_MORPH_DILATE_ITERATIONS)
                : jitterInt(random, baseline.getMorphologyDilateIterations(),
                MIN_MORPH_DILATE_ITERATIONS, MAX_MORPH_DILATE_ITERATIONS, 0.45);

        double normalizedMaskThreshold = wideExploration
                ? randomDouble(random, MIN_MASK_THRESHOLD, MAX_MASK_THRESHOLD)
                : jitterDouble(random, baseline.getNormalizedMaskThreshold(),
                MIN_MASK_THRESHOLD, MAX_MASK_THRESHOLD, 0.45);

        return snapWholeNumericGenes(new TrackingConfiguration(
                maxFramesDisappeared,
                minContourArea,
                maxRectCircumference,
                maxVerticalDisplacement,
                minHorizontalMovement,
                maxAssociationDistance,
                mog2History,
                mog2VarThreshold,
                detectShadows,
                morphologyKernelSize,
                morphologyOpenIterations,
                morphologyDilateIterations,
                normalizedMaskThreshold).normalized());
    }

    private static TrackingConfiguration crossover(
            TrackingConfiguration a,
            TrackingConfiguration b,
            Random random) {
        return snapWholeNumericGenes(new TrackingConfiguration(
                random.nextBoolean() ? a.getMaxFramesDisappeared() : b.getMaxFramesDisappeared(),
                random.nextBoolean() ? a.getMinContourArea() : b.getMinContourArea(),
                random.nextBoolean() ? a.getMaxRectCircumference() : b.getMaxRectCircumference(),
                random.nextBoolean() ? a.getMaxVerticalDisplacementPixels() : b.getMaxVerticalDisplacementPixels(),
                random.nextBoolean() ? a.getMinHorizontalMovementPixels() : b.getMinHorizontalMovementPixels(),
                random.nextBoolean() ? a.getMaxAssociationDistancePixels() : b.getMaxAssociationDistancePixels(),
                random.nextBoolean() ? a.getMog2HistoryFrames() : b.getMog2HistoryFrames(),
                random.nextBoolean() ? a.getMog2VarThreshold() : b.getMog2VarThreshold(),
                random.nextBoolean() ? a.isMog2DetectShadows() : b.isMog2DetectShadows(),
                random.nextBoolean() ? a.getMorphologyKernelSize() : b.getMorphologyKernelSize(),
                random.nextBoolean() ? a.getMorphologyOpenIterations() : b.getMorphologyOpenIterations(),
                random.nextBoolean() ? a.getMorphologyDilateIterations() : b.getMorphologyDilateIterations(),
                random.nextBoolean() ? a.getNormalizedMaskThreshold() : b.getNormalizedMaskThreshold()).normalized());
    }

    private static TrackingConfiguration mutate(
            TrackingConfiguration current,
            double mutationRate,
            Random random) {
        int maxFramesDisappeared = maybeMutateInt(
                random, current.getMaxFramesDisappeared(),
                MIN_MAX_FRAMES_DISAPPEARED, MAX_MAX_FRAMES_DISAPPEARED, mutationRate, 0.30);
        double minContourArea = maybeMutateDouble(
                random, current.getMinContourArea(),
                MIN_MIN_CONTOUR_AREA, MAX_MIN_CONTOUR_AREA, mutationRate, 0.30);
        double maxRectCircumference = maybeMutateDouble(
                random, current.getMaxRectCircumference(),
                MIN_MAX_RECT_CIRCUMFERENCE, MAX_MAX_RECT_CIRCUMFERENCE, mutationRate, 0.30);
        double maxVerticalDisplacement = maybeMutateDouble(
                random, current.getMaxVerticalDisplacementPixels(),
                MIN_MAX_VERTICAL_DISPLACEMENT, MAX_MAX_VERTICAL_DISPLACEMENT, mutationRate, 0.30);
        double minHorizontalMovement = maybeMutateDouble(
                random, current.getMinHorizontalMovementPixels(),
                MIN_MIN_HORIZONTAL_MOVEMENT, MAX_MIN_HORIZONTAL_MOVEMENT, mutationRate, 0.30);
        double maxAssociationDistance = maybeMutateDouble(
                random, current.getMaxAssociationDistancePixels(),
                MIN_MAX_ASSOCIATION_DISTANCE, MAX_MAX_ASSOCIATION_DISTANCE, mutationRate, 0.30);
        int mog2History = maybeMutateInt(
                random, current.getMog2HistoryFrames(),
                MIN_MOG2_HISTORY, MAX_MOG2_HISTORY, mutationRate, 0.30);
        double mog2VarThreshold = maybeMutateDouble(
                random, current.getMog2VarThreshold(),
                MIN_MOG2_VAR_THRESHOLD, MAX_MOG2_VAR_THRESHOLD, mutationRate, 0.30);
        boolean detectShadows = random.nextDouble() < mutationRate
                ? !current.isMog2DetectShadows()
                : current.isMog2DetectShadows();
        int morphologyKernelSize = maybeMutateInt(
                random, current.getMorphologyKernelSize(),
                MIN_MORPH_KERNEL_SIZE, MAX_MORPH_KERNEL_SIZE, mutationRate, 0.30);
        int morphologyOpenIterations = maybeMutateInt(
                random, current.getMorphologyOpenIterations(),
                MIN_MORPH_OPEN_ITERATIONS, MAX_MORPH_OPEN_ITERATIONS, mutationRate, 0.30);
        int morphologyDilateIterations = maybeMutateInt(
                random, current.getMorphologyDilateIterations(),
                MIN_MORPH_DILATE_ITERATIONS, MAX_MORPH_DILATE_ITERATIONS, mutationRate, 0.30);
        double normalizedMaskThreshold = maybeMutateDouble(
                random, current.getNormalizedMaskThreshold(),
                MIN_MASK_THRESHOLD, MAX_MASK_THRESHOLD, mutationRate, 0.30);

        return snapWholeNumericGenes(new TrackingConfiguration(
                maxFramesDisappeared,
                minContourArea,
                maxRectCircumference,
                maxVerticalDisplacement,
                minHorizontalMovement,
                maxAssociationDistance,
                mog2History,
                mog2VarThreshold,
                detectShadows,
                morphologyKernelSize,
                morphologyOpenIterations,
                morphologyDilateIterations,
                normalizedMaskThreshold).normalized());
    }

    private static int maybeMutateInt(
            Random random,
            int value,
            int min,
            int max,
            double mutationRate,
            double sigmaFactor) {
        if (random.nextDouble() >= mutationRate) {
            return value;
        }
        if (random.nextDouble() < 0.20) {
            return randomInt(random, min, max);
        }
        return jitterInt(random, value, min, max, sigmaFactor);
    }

    private static double maybeMutateDouble(
            Random random,
            double value,
            double min,
            double max,
            double mutationRate,
            double sigmaFactor) {
        if (random.nextDouble() >= mutationRate) {
            return value;
        }
        if (random.nextDouble() < 0.20) {
            return randomDouble(random, min, max);
        }
        return jitterDouble(random, value, min, max, sigmaFactor);
    }

    private static int jitterInt(
            Random random,
            int center,
            int min,
            int max,
            double sigmaFactor) {
        double sigma = Math.max(1.0, Math.abs(center) * sigmaFactor + 1.0);
        int mutated = (int) Math.round(center + random.nextGaussian() * sigma);
        return clampInt(mutated, min, max);
    }

    private static double jitterDouble(
            Random random,
            double center,
            double min,
            double max,
            double sigmaFactor) {
        double sigma = Math.max(0.0001, Math.abs(center) * sigmaFactor + 0.0001);
        double mutated = center + random.nextGaussian() * sigma;
        return clampDouble(mutated, min, max);
    }

    private static int randomInt(Random random, int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    private static double randomDouble(Random random, double min, double max) {
        return min + random.nextDouble() * (max - min);
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clampDouble(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static TrackingConfiguration snapWholeNumericGenes(TrackingConfiguration cfg) {
        return new TrackingConfiguration(
                cfg.getMaxFramesDisappeared(),
                roundWhole(cfg.getMinContourArea()),
                roundWhole(cfg.getMaxRectCircumference()),
                roundWhole(cfg.getMaxVerticalDisplacementPixels()),
                roundWhole(cfg.getMinHorizontalMovementPixels()),
                roundWhole(cfg.getMaxAssociationDistancePixels()),
                cfg.getMog2HistoryFrames(),
                roundWhole(cfg.getMog2VarThreshold()),
                cfg.isMog2DetectShadows(),
                cfg.getMorphologyKernelSize(),
                cfg.getMorphologyOpenIterations(),
                cfg.getMorphologyDilateIterations(),
                roundWhole(cfg.getNormalizedMaskThreshold()))
                .normalized();
    }

    private static double roundWhole(double value) {
        return Math.rint(value);
    }

    private static void printGenerationSummary(
            int generation,
            int totalGenerations,
            Candidate best,
            double averageScore) {
        DecimalFormat scoreFormat = new DecimalFormat("0.0000");
        DecimalFormat metricFormat = new DecimalFormat("0.000");

        String mae = formatMetric(best.metrics().maeVelocityPxPerSec(), metricFormat);
        String w1Time = formatMetric(best.metrics().wassersteinTimeSec(), metricFormat);
        String w1Velocity = formatMetric(best.metrics().wassersteinVelocityPxPerSec(), metricFormat);

        System.out.println("Generation " + generation + "/" + totalGenerations
                + "  bestScore=" + scoreFormat.format(best.fitness())
                + "  avgScore=" + scoreFormat.format(averageScore)
                + "  F1=" + scoreFormat.format(best.metrics().f1())
                + "  W1_time=" + w1Time
                + "  W1_velocity=" + w1Velocity
                + "  MAE_velocity=" + mae);
    }

    private static void printFinalSummary(
            Candidate baseline,
            Candidate best,
            Path bestConfigPath,
            Path historyCsvPath,
            Path reportPath) {
        DecimalFormat scoreFormat = new DecimalFormat("0.0000");
        DecimalFormat metricFormat = new DecimalFormat("0.000");

        double scoreDelta = best.fitness() - baseline.fitness();
        double scorePct = Math.abs(baseline.fitness()) > EPS
                ? (scoreDelta / Math.abs(baseline.fitness())) * 100.0
                : Double.NaN;

        System.out.println();
        System.out.println("GA tuning complete");
        System.out.println("------------------");
        System.out.println("Baseline score: " + scoreFormat.format(baseline.fitness()));
        System.out.println("Best score:     " + scoreFormat.format(best.fitness()));
        System.out.println("Delta:          " + scoreFormat.format(scoreDelta) + " (" + formatPercent(scorePct) + ")");
        System.out.println();
        System.out.println("Baseline metrics: F1=" + scoreFormat.format(baseline.metrics().f1())
                + ", W1_time=" + formatMetric(baseline.metrics().wassersteinTimeSec(), metricFormat)
                + ", W1_velocity=" + formatMetric(baseline.metrics().wassersteinVelocityPxPerSec(), metricFormat)
                + ", MAE_velocity=" + formatMetric(baseline.metrics().maeVelocityPxPerSec(), metricFormat));
        System.out.println("Best metrics:     F1=" + scoreFormat.format(best.metrics().f1())
                + ", W1_time=" + formatMetric(best.metrics().wassersteinTimeSec(), metricFormat)
                + ", W1_velocity=" + formatMetric(best.metrics().wassersteinVelocityPxPerSec(), metricFormat)
                + ", MAE_velocity=" + formatMetric(best.metrics().maeVelocityPxPerSec(), metricFormat));
        System.out.println();
        System.out.println("Best configuration:");
        printConfiguration(best.configuration());
        System.out.println();
        System.out.println("Original configuration:");
        printConfiguration(baseline.configuration());
        System.out.println();
        System.out.println("Wrote best config: " + bestConfigPath.toAbsolutePath());
        System.out.println("Wrote history CSV: " + historyCsvPath.toAbsolutePath());
        System.out.println("Wrote report:      " + reportPath.toAbsolutePath());
    }

    private static void printConfiguration(TrackingConfiguration config) {
        System.out.println("maxFramesDisappeared=" + config.getMaxFramesDisappeared());
        System.out.println("minContourArea=" + formatDouble(config.getMinContourArea()));
        System.out.println("maxRectCircumference=" + formatDouble(config.getMaxRectCircumference()));
        System.out.println("maxVerticalDisplacementPixels=" + formatDouble(config.getMaxVerticalDisplacementPixels()));
        System.out.println("minHorizontalMovementPixels=" + formatDouble(config.getMinHorizontalMovementPixels()));
        System.out.println("maxAssociationDistancePixels=" + formatDouble(config.getMaxAssociationDistancePixels()));
        System.out.println("mog2HistoryFrames=" + config.getMog2HistoryFrames());
        System.out.println("mog2VarThreshold=" + formatDouble(config.getMog2VarThreshold()));
        System.out.println("mog2DetectShadows=" + config.isMog2DetectShadows());
        System.out.println("morphologyKernelSize=" + config.getMorphologyKernelSize());
        System.out.println("morphologyOpenIterations=" + config.getMorphologyOpenIterations());
        System.out.println("morphologyDilateIterations=" + config.getMorphologyDilateIterations());
        System.out.println("normalizedMaskThreshold=" + formatDouble(config.getNormalizedMaskThreshold()));
    }

    private static void writeHistoryCsv(Path output, List<GenerationSummary> history) throws IOException {
        Path parent = output.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(output))) {
            writer.println("generation,best_score,avg_score,best_f1,best_w1_time_sec,best_w1_velocity_px_per_sec,best_mae_velocity_px_per_sec");
            for (GenerationSummary row : history) {
                writer.printf(Locale.US, "%d,%.6f,%.6f,%.6f,%s,%s,%s%n",
                        row.generation(),
                        row.bestScore(),
                        row.averageScore(),
                        row.bestF1(),
                        toCsvMetric(row.bestW1TimeSec()),
                        toCsvMetric(row.bestW1VelocityPxPerSec()),
                        toCsvMetric(row.bestMaeVelocityPxPerSec()));
            }
        }
    }

    private static void writeFinalReport(Path output, Candidate baseline, Candidate best) throws IOException {
        Path parent = output.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(output))) {
            writer.println("Ground Truth GA Tuning Report");
            writer.println();
            writer.printf(Locale.US, "baseline_score=%.6f%n", baseline.fitness());
            writer.printf(Locale.US, "best_score=%.6f%n", best.fitness());
            writer.printf(Locale.US, "score_delta=%.6f%n", best.fitness() - baseline.fitness());
            writer.println();
            writer.println("[baseline_metrics]");
            writeMetricsBlock(writer, baseline.metrics());
            writer.println();
            writer.println("[best_metrics]");
            writeMetricsBlock(writer, best.metrics());
            writer.println();
            writer.println("[best_configuration]");
            writeConfigBlock(writer, best.configuration());
        }
    }

    private static void writeMetricsBlock(PrintWriter writer, GroundTruthEvaluator.EvaluationResult metrics) {
        writer.printf(Locale.US, "f1=%.6f%n", metrics.f1());
        writer.printf(Locale.US, "precision=%.6f%n", metrics.precision());
        writer.printf(Locale.US, "recall=%.6f%n", metrics.recall());
        writer.printf(Locale.US, "w1_time_sec=%s%n", toCsvMetric(metrics.wassersteinTimeSec()));
        writer.printf(Locale.US, "w1_velocity_px_per_sec=%s%n", toCsvMetric(metrics.wassersteinVelocityPxPerSec()));
        writer.printf(Locale.US, "mae_velocity_px_per_sec=%s%n", toCsvMetric(metrics.maeVelocityPxPerSec()));
        writer.printf(Locale.US, "tp=%d%n", metrics.truePositives());
        writer.printf(Locale.US, "fp=%d%n", metrics.falsePositives());
        writer.printf(Locale.US, "fn=%d%n", metrics.falseNegatives());
    }

    private static void writeConfigBlock(PrintWriter writer, TrackingConfiguration config) {
        writer.println("maxFramesDisappeared=" + config.getMaxFramesDisappeared());
        writer.println("minContourArea=" + formatDouble(config.getMinContourArea()));
        writer.println("maxRectCircumference=" + formatDouble(config.getMaxRectCircumference()));
        writer.println("maxVerticalDisplacementPixels=" + formatDouble(config.getMaxVerticalDisplacementPixels()));
        writer.println("minHorizontalMovementPixels=" + formatDouble(config.getMinHorizontalMovementPixels()));
        writer.println("maxAssociationDistancePixels=" + formatDouble(config.getMaxAssociationDistancePixels()));
        writer.println("mog2HistoryFrames=" + config.getMog2HistoryFrames());
        writer.println("mog2VarThreshold=" + formatDouble(config.getMog2VarThreshold()));
        writer.println("mog2DetectShadows=" + config.isMog2DetectShadows());
        writer.println("morphologyKernelSize=" + config.getMorphologyKernelSize());
        writer.println("morphologyOpenIterations=" + config.getMorphologyOpenIterations());
        writer.println("morphologyDilateIterations=" + config.getMorphologyDilateIterations());
        writer.println("normalizedMaskThreshold=" + formatDouble(config.getNormalizedMaskThreshold()));
    }

    private static String toCsvMetric(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "";
        }
        return String.format(Locale.US, "%.6f", value);
    }

    private static String formatMetric(double value, DecimalFormat formatter) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "NA";
        }
        return formatter.format(value);
    }

    private static String formatPercent(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "NA";
        }
        return String.format(Locale.US, "%.2f%%", value);
    }

    private static String formatDouble(double value) {
        double rounded = Math.rint(value);
        if (Math.abs(value - rounded) < 1.0e-9) {
            return Long.toString((long) rounded);
        }
        return String.format(Locale.US, "%.6f", value);
    }

    private static boolean containsHelp(String[] args) {
        for (String arg : args) {
            if ("--help".equals(arg) || "-h".equals(arg)) {
                return true;
            }
        }
        return false;
    }

    private static void printUsage() {
        String usage = """
                Usage:
                  GroundTruthGeneticTunerCli --video=<videoPath> --truth-events=<eventsCsvPath> --tracking-config=<config.properties> --generations=<int> --population=<int> --mutation-rate=<double> [options]

                Required:
                  --video=<path>
                  --truth-events=<path to *_events.csv>
                  --tracking-config=<path to starting tracking config>
                  --generations=<positive integer>
                  --population=<positive integer>  (alias: --population-pool)
                  --mutation-rate=<0.0..1.0>

                Optional:
                  --match-window-sec=<double>   default 1.0
                  --fps=<double>                optional fallback FPS
                  --seed=<long>                 default current time
                  --crossover-rate=<0.0..1.0>   default 0.75
                  --elite-count=<int>           default max(1, population/10)
                  --candidate-timeout-sec=<double>  default 45.0 (set 0 for no timeout)
                  --max-timeout-sec=<double>        alias of --candidate-timeout-sec
                  --max-frames=<int>            default 0 (full video); >0 for quicker tuning
                  --output-dir=<path>           default ./ga-tuning-output
                  --best-config-out=<path>      default <output-dir>/ga_best_tracking_config.properties
                  --history-out=<path>          default <output-dir>/ga_history.csv
                  --report-out=<path>           default <output-dir>/ga_report.txt
                """;
        System.err.println(usage);
    }

    private record Options(
            Path videoPath,
            Path truthEventsCsv,
            Path trackingConfigPath,
            int generations,
            int populationSize,
            double mutationRate,
            double crossoverRate,
            int eliteCount,
            double matchWindowSeconds,
            Double fpsOverride,
            double candidateTimeoutSeconds,
            int maxFramesToProcess,
            long seed,
            Path outputDir,
            Path bestConfigPath,
            Path historyCsvPath,
            Path reportPath) {

        private static Options parse(String[] args) {
            ParseResult parsed = parseArgs(args);
            if (!parsed.positional().isEmpty()) {
                throw new IllegalArgumentException("Unexpected positional arguments: " + parsed.positional());
            }

            validateKnownOptions(parsed.options());

            Path videoPath = Path.of(requireOption(parsed.options(), "video"));
            Path truthEventsPath = Path.of(requireOption(parsed.options(), "truthEvents"));
            Path trackingConfigPath = Path.of(requireOption(parsed.options(), "trackingConfig"));
            int generations = parsePositiveInt(requireOption(parsed.options(), "generations"), "--generations");

            String populationRaw = findOption(parsed.options(), "population");
            if (populationRaw == null) {
                populationRaw = findOption(parsed.options(), "populationPool");
            }
            if (populationRaw == null || populationRaw.isBlank()) {
                throw new IllegalArgumentException("Missing required option --population (or --population-pool)");
            }
            int population = parsePositiveInt(populationRaw, "--population");

            double mutationRate = parseRate(requireOption(parsed.options(), "mutationRate"), "--mutation-rate");
            String crossoverRaw = findOption(parsed.options(), "crossoverRate");
            double crossoverRate = crossoverRaw == null ? DEFAULT_CROSSOVER_RATE : parseRate(crossoverRaw, "--crossover-rate");

            String eliteRaw = findOption(parsed.options(), "eliteCount");
            int eliteCount = eliteRaw == null ? Math.max(1, population / 10) : parsePositiveInt(eliteRaw, "--elite-count");
            eliteCount = Math.min(eliteCount, population);

            String matchWindowRaw = findOption(parsed.options(), "matchWindowSec");
            double matchWindowSeconds = matchWindowRaw == null ? 1.0 : parsePositiveDouble(matchWindowRaw, "--match-window-sec");

            String fpsRaw = findOption(parsed.options(), "fps");
            Double fpsOverride = null;
            if (fpsRaw != null && !fpsRaw.isBlank()) {
                fpsOverride = parsePositiveDouble(fpsRaw, "--fps");
            }

            String timeoutRaw = findOption(parsed.options(), "candidateTimeoutSec");
            if (timeoutRaw == null || timeoutRaw.isBlank()) {
                timeoutRaw = findOption(parsed.options(), "maxTimeoutSec");
            }
            double candidateTimeoutSeconds = timeoutRaw == null
                    ? DEFAULT_CANDIDATE_TIMEOUT_SECONDS
                    : parseNonNegativeDouble(timeoutRaw, "--candidate-timeout-sec");

            String maxFramesRaw = findOption(parsed.options(), "maxFrames");
            int maxFramesToProcess = maxFramesRaw == null ? 0 : parseNonNegativeInt(maxFramesRaw, "--max-frames");

            String seedRaw = findOption(parsed.options(), "seed");
            long seed = seedRaw == null ? System.currentTimeMillis() : parseLong(seedRaw, "--seed");

            Path outputDir = Path.of(findOptionOrDefault(parsed.options(), "outputDir", "./ga-tuning-output")).toAbsolutePath();
            Path bestConfigPath = resolveOptionalPath(parsed.options(), "bestConfigOut", outputDir.resolve("ga_best_tracking_config.properties"));
            Path historyCsvPath = resolveOptionalPath(parsed.options(), "historyOut", outputDir.resolve("ga_history.csv"));
            Path reportPath = resolveOptionalPath(parsed.options(), "reportOut", outputDir.resolve("ga_report.txt"));

            if (!Files.exists(videoPath)) {
                throw new IllegalArgumentException("Video file does not exist: " + videoPath);
            }
            if (!Files.exists(truthEventsPath)) {
                throw new IllegalArgumentException("Truth events CSV does not exist: " + truthEventsPath);
            }
            if (!Files.exists(trackingConfigPath)) {
                throw new IllegalArgumentException("Tracking config file does not exist: " + trackingConfigPath);
            }

            return new Options(
                    videoPath.toAbsolutePath(),
                    truthEventsPath.toAbsolutePath(),
                    trackingConfigPath.toAbsolutePath(),
                    generations,
                    population,
                    mutationRate,
                    crossoverRate,
                    eliteCount,
                    matchWindowSeconds,
                    fpsOverride,
                    candidateTimeoutSeconds,
                    maxFramesToProcess,
                    seed,
                    outputDir,
                    bestConfigPath,
                    historyCsvPath,
                    reportPath);
        }

        private static ParseResult parseArgs(String[] args) {
            List<String> positional = new ArrayList<>();
            Map<String, String> options = new LinkedHashMap<>();

            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if (arg == null || arg.isBlank()) {
                    continue;
                }
                if (!arg.startsWith("--")) {
                    positional.add(arg);
                    continue;
                }

                String key;
                String value;
                int eq = arg.indexOf('=');
                if (eq >= 0) {
                    key = arg.substring(2, eq);
                    value = arg.substring(eq + 1);
                } else {
                    key = arg.substring(2);
                    if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                        value = args[++i];
                    } else {
                        value = "true";
                    }
                }
                options.put(key, value);
            }
            return new ParseResult(positional, options);
        }

        private static void validateKnownOptions(Map<String, String> options) {
            for (String rawKey : options.keySet()) {
                String key = TrackingConfigurationIO.normalizeKey(rawKey);
                if (isKnownOption(key)) {
                    continue;
                }
                throw new IllegalArgumentException("Unknown option: --" + rawKey);
            }
        }

        private static boolean isKnownOption(String normalizedKey) {
            return switch (normalizedKey) {
                case "video",
                     "truthevents",
                     "trackingconfig",
                     "generations",
                     "population",
                     "populationpool",
                     "mutationrate",
                     "crossoverrate",
                     "elitecount",
                     "candidatetimeoutsec",
                     "maxtimeoutsec",
                     "maxframes",
                     "matchwindowsec",
                     "fps",
                     "seed",
                     "outputdir",
                     "bestconfigout",
                     "historyout",
                     "reportout" -> true;
                default -> false;
            };
        }

        private static Path resolveOptionalPath(Map<String, String> options, String canonicalName, Path defaultPath) {
            String value = findOption(options, canonicalName);
            return value == null || value.isBlank() ? defaultPath.toAbsolutePath() : Path.of(value).toAbsolutePath();
        }

        private static String findOptionOrDefault(Map<String, String> options, String canonicalName, String defaultValue) {
            String value = findOption(options, canonicalName);
            return (value == null || value.isBlank()) ? defaultValue : value;
        }

        private static String requireOption(Map<String, String> options, String canonicalName) {
            String value = findOption(options, canonicalName);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Missing required option --" + canonicalName);
            }
            return value;
        }

        private static String findOption(Map<String, String> options, String canonicalName) {
            String target = TrackingConfigurationIO.normalizeKey(canonicalName);
            for (Map.Entry<String, String> entry : options.entrySet()) {
                if (target.equals(TrackingConfigurationIO.normalizeKey(entry.getKey()))) {
                    return entry.getValue();
                }
            }
            return null;
        }

        private static int parsePositiveInt(String raw, String name) {
            try {
                int value = Integer.parseInt(raw.trim());
                if (value <= 0) {
                    throw new IllegalArgumentException(name + " must be > 0");
                }
                return value;
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid integer for " + name + ": " + raw);
            }
        }

        private static int parseNonNegativeInt(String raw, String name) {
            try {
                int value = Integer.parseInt(raw.trim());
                if (value < 0) {
                    throw new IllegalArgumentException(name + " must be >= 0");
                }
                return value;
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid integer for " + name + ": " + raw);
            }
        }

        private static long parseLong(String raw, String name) {
            try {
                return Long.parseLong(raw.trim());
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid long value for " + name + ": " + raw);
            }
        }

        private static double parsePositiveDouble(String raw, String name) {
            try {
                double value = Double.parseDouble(raw.trim());
                if (value <= 0.0 || Double.isNaN(value) || Double.isInfinite(value)) {
                    throw new IllegalArgumentException(name + " must be a finite number > 0");
                }
                return value;
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid numeric value for " + name + ": " + raw);
            }
        }

        private static double parseNonNegativeDouble(String raw, String name) {
            try {
                double value = Double.parseDouble(raw.trim());
                if (!Double.isFinite(value) || value < 0.0) {
                    throw new IllegalArgumentException(name + " must be a finite number >= 0");
                }
                return value;
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid numeric value for " + name + ": " + raw);
            }
        }

        private static double parseRate(String raw, String name) {
            try {
                double value = Double.parseDouble(raw.trim());
                if (Double.isNaN(value) || Double.isInfinite(value) || value < 0.0 || value > 1.0) {
                    throw new IllegalArgumentException(name + " must be between 0.0 and 1.0");
                }
                return value;
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid numeric value for " + name + ": " + raw);
            }
        }
    }

    private record ParseResult(List<String> positional, Map<String, String> options) {
    }

    private record Candidate(
            TrackingConfiguration configuration,
            GroundTruthEvaluator.EvaluationResult metrics,
            double fitness,
            String error) {
        private static Candidate success(
                TrackingConfiguration configuration,
                GroundTruthEvaluator.EvaluationResult metrics,
                double fitness) {
            return new Candidate(configuration, metrics, fitness, null);
        }

        private static Candidate failure(TrackingConfiguration configuration, String error) {
            GroundTruthEvaluator.EvaluationResult empty = new GroundTruthEvaluator.EvaluationResult(
                    Path.of(""), Path.of(""),
                    0, 0, 0, 0, 0,
                    0.0, 0.0, 0.0,
                    Double.NaN, Double.NaN, Double.NaN,
                    0.0, Double.NaN, 0);
            return new Candidate(configuration, empty, 0.0, error);
        }
    }

    private record GenerationSummary(
            int generation,
            double bestScore,
            double averageScore,
            double bestF1,
            double bestW1TimeSec,
            double bestW1VelocityPxPerSec,
            double bestMaeVelocityPxPerSec) {
    }

    private static final class FitnessScorer {
        private final GroundTruthEvaluator.EvaluationResult baseline;

        private FitnessScorer(GroundTruthEvaluator.EvaluationResult baseline) {
            this.baseline = baseline;
        }

        private double score(GroundTruthEvaluator.EvaluationResult candidate) {
            double f1 = bounded(candidate.f1(), 0.0, 1.0);
            double timeGain = improvementRatio(baseline.wassersteinTimeSec(), candidate.wassersteinTimeSec());
            double velocityGain = improvementRatio(
                    baseline.wassersteinVelocityPxPerSec(),
                    candidate.wassersteinVelocityPxPerSec());
            double maeGain = improvementRatio(baseline.maeVelocityPxPerSec(), candidate.maeVelocityPxPerSec());

            return 0.50 * f1 + 0.20 * timeGain + 0.20 * velocityGain + 0.10 * maeGain;
        }

        private double improvementRatio(double baselineValue, double candidateValue) {
            if (!Double.isFinite(candidateValue) || candidateValue < 0.0) {
                return 0.0;
            }
            if (!Double.isFinite(baselineValue) || baselineValue < EPS) {
                if (candidateValue < EPS) {
                    return 1.0;
                }
                return 1.0 / (1.0 + candidateValue);
            }

            double ratio = baselineValue / (candidateValue + EPS);
            return bounded(ratio, 0.0, MAX_RATIO_CAP);
        }

        private double bounded(double value, double min, double max) {
            if (!Double.isFinite(value)) {
                return min;
            }
            return Math.max(min, Math.min(max, value));
        }
    }
}
