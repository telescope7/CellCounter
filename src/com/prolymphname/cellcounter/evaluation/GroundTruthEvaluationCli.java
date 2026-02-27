package com.prolymphname.cellcounter.evaluation;

import com.prolymphname.cellcounter.HeadlessProcessor;
import com.prolymphname.cellcounter.OpenCvSupport;
import com.prolymphname.cellcounter.application.CellCounterApplicationService;
import com.prolymphname.cellcounter.trackingadapter.TrackingConfiguration;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public final class GroundTruthEvaluationCli {
    private GroundTruthEvaluationCli() {
    }

    public static void main(String[] args) {
        OpenCvSupport.loadOpenCv();
        try {
            EvaluationOptions options = EvaluationOptions.parse(args);
            run(options);
        } catch (IllegalArgumentException ex) {
            System.err.println("Invalid arguments: " + ex.getMessage());
            printUsage();
            System.exit(2);
        } catch (Exception ex) {
            System.err.println("Evaluation failed: " + ex.getMessage());
            ex.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void run(EvaluationOptions options) throws IOException {
        CellCounterApplicationService appService = new CellCounterApplicationService();
        appService.setTrackingConfiguration(options.trackingConfiguration());

        HeadlessProcessor processor = new HeadlessProcessor(appService);
        processor.run(
                options.videoPath().toAbsolutePath().toString(),
                options.outputPrefix(),
                options.cellType(),
                options.substrate(),
                options.flow());

        Path analysisCsv = Path.of(options.outputPrefix() + "_analysis.csv").toAbsolutePath();
        if (!Files.exists(analysisCsv)) {
            throw new IllegalStateException("Expected analysis CSV was not generated: " + analysisCsv);
        }

        GroundTruthEvaluator evaluator = new GroundTruthEvaluator();
        GroundTruthEvaluator.EvaluationResult result = evaluator.evaluate(
                options.truthEventsCsv().toAbsolutePath(),
                analysisCsv,
                options.matchWindowSeconds(),
                options.fpsOverride());

        GroundTruthEvaluator.EvaluationResult baselineForScore = null;
        Double gaScore = null;
        Double baselineScore = null;
        if (options.scoreBaselineConfigPath() != null) {
            TrackingConfiguration baselineConfig = TrackingConfigurationIO.loadFromProperties(
                    options.scoreBaselineConfigPath());
            if (TrackingConfigurationIO.canonicalKey(baselineConfig)
                    .equals(TrackingConfigurationIO.canonicalKey(options.trackingConfiguration()))) {
                baselineForScore = result;
            } else {
                Path outputPrefixPath = Path.of(options.outputPrefix()).toAbsolutePath();
                Path tmpWorkspace = outputPrefixPath.getParent() == null
                        ? Path.of(".").toAbsolutePath().resolve("eval_score_tmp")
                        : outputPrefixPath.getParent().resolve("eval_score_tmp");
                GroundTruthComparisonService comparisonService = new GroundTruthComparisonService(
                        options.videoPath(),
                        options.truthEventsCsv(),
                        options.matchWindowSeconds(),
                        options.fpsOverride(),
                        tmpWorkspace,
                        0.0,
                        0);
                baselineForScore = comparisonService.evaluate(baselineConfig);
            }
            gaScore = GaFitnessScoring.score(baselineForScore, result);
            baselineScore = GaFitnessScoring.score(baselineForScore, baselineForScore);
        }

        printResult(result, gaScore, baselineScore, options.scoreBaselineConfigPath());
        Path metricsOutput = options.metricsOutputPath().toAbsolutePath();
        writeMetricsCsv(metricsOutput, result, gaScore, baselineScore, options.scoreBaselineConfigPath());
        System.out.println("Wrote evaluation metrics: " + metricsOutput);
    }

    private static void printResult(
            GroundTruthEvaluator.EvaluationResult result,
            Double gaScore,
            Double baselineScore,
            Path scoreBaselineConfigPath) {
        DecimalFormat f3 = new DecimalFormat("0.000");
        DecimalFormat f4 = new DecimalFormat("0.0000");

        System.out.println();
        System.out.println("CellCounter Ground-Truth Evaluation");
        System.out.println("-----------------------------------");
        System.out.println("Truth CSV:      " + result.truthEventsCsv());
        System.out.println("Analysis CSV:   " + result.predictedAnalysisCsv());
        System.out.println("Inferred FPS:   " + f3.format(result.inferredFps()));
        System.out.println("Match Window:   " + f3.format(result.matchWindowSeconds()) + " sec");
        System.out.println();
        System.out.println("Counts");
        System.out.println("  Truth Tracks:      " + result.truthCount());
        System.out.println("  Predicted Tracks:  " + result.predictedCount());
        System.out.println("  TP / FP / FN:      " + result.truePositives() + " / "
                + result.falsePositives() + " / " + result.falseNegatives());
        System.out.println();
        System.out.println("Metrics");
        System.out.println("  Precision:         " + f4.format(result.precision()));
        System.out.println("  Recall:            " + f4.format(result.recall()));
        System.out.println("  F1:                " + f4.format(result.f1()));
        System.out.println("  W1_time (sec):     " + formatMetric(result.wassersteinTimeSec(), f4));
        System.out.println("  W1_velocity (px/s):" + formatMetric(result.wassersteinVelocityPxPerSec(), f4));
        System.out.println("  MAE_velocity (px/s): " + formatMetric(result.maeVelocityPxPerSec(), f4));
        if (gaScore != null) {
            System.out.println("  GA score:          " + formatMetric(gaScore, f4));
            System.out.println("  GA baseline score: " + formatMetric(baselineScore, f4));
            System.out.println("  Score baseline cfg:" + scoreBaselineConfigPath.toAbsolutePath());
        } else {
            System.out.println("  GA score:          NA (provide --score-baseline-config to compute)");
        }
    }

    private static String formatMetric(double value, DecimalFormat formatter) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "NA";
        }
        return formatter.format(value);
    }

    private static void writeMetricsCsv(
            Path output,
            GroundTruthEvaluator.EvaluationResult result,
            Double gaScore,
            Double baselineScore,
            Path scoreBaselineConfigPath) throws IOException {
        Path parent = output.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(output))) {
            writer.println("truth_csv,analysis_csv,truth_count,predicted_count,tp,fp,fn,precision,recall,f1,w1_time_sec,w1_velocity_px_per_sec,mae_velocity_px_per_sec,match_window_sec,inferred_fps,matched_velocity_count,ga_score,ga_baseline_score,score_baseline_config");
            writer.printf("%s,%s,%d,%d,%d,%d,%d,%.6f,%.6f,%.6f,%s,%s,%s,%.6f,%.6f,%d,%s,%s,%s%n",
                    escapeCsv(result.truthEventsCsv().toString()),
                    escapeCsv(result.predictedAnalysisCsv().toString()),
                    result.truthCount(),
                    result.predictedCount(),
                    result.truePositives(),
                    result.falsePositives(),
                    result.falseNegatives(),
                    result.precision(),
                    result.recall(),
                    result.f1(),
                    formatCsvDouble(result.wassersteinTimeSec()),
                    formatCsvDouble(result.wassersteinVelocityPxPerSec()),
                    formatCsvDouble(result.maeVelocityPxPerSec()),
                    result.matchWindowSeconds(),
                    result.inferredFps(),
                    result.matchedVelocityCount(),
                    formatCsvDouble(gaScore == null ? Double.NaN : gaScore),
                    formatCsvDouble(baselineScore == null ? Double.NaN : baselineScore),
                    scoreBaselineConfigPath == null ? "" : escapeCsv(scoreBaselineConfigPath.toAbsolutePath().toString()));
        }
    }

    private static String formatCsvDouble(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "";
        }
        return String.format("%.6f", value);
    }

    private static String escapeCsv(String value) {
        if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static void printUsage() {
        String usage = """
                Usage:
                  GroundTruthEvaluationCli --video=<videoPath> --truth-events=<eventsCsvPath> --output-prefix=<outputPrefix> [options]

                Required:
                  --video=<path>
                  --truth-events=<path to *_events.csv>
                  --output-prefix=<path prefix for generated CellCounter outputs>

                Evaluation options:
                  --match-window-sec=<double>   (default: 1.0)
                  --fps=<double>                (optional fallback if FPS cannot be inferred from truth CSV)
                  --metrics-out=<path>          (default: <output-prefix>_evaluation_metrics.csv)
                  --score-baseline-config=<path to baseline tracking config used by GA>
                  --baseline-tracking-config=<path>   alias of --score-baseline-config

                Metadata options (forwarded to analysis CSV):
                  --cellType=<value> --substrate=<value> --flow=<value>

                Tracking options:
                  --tracking-config=<file.properties>
                  --maxFramesDisappeared=<int>
                  --minContourArea=<double>
                  --maxRectCircumference=<double>
                  --maxVerticalDisplacementPixels=<double>
                  --minHorizontalMovementPixels=<double>
                  --maxAssociationDistancePixels=<double>
                  --mog2HistoryFrames=<int>
                  --mog2VarThreshold=<double>
                  --mog2DetectShadows=<true|false>
                  --morphologyKernelSize=<int>
                  --morphologyOpenIterations=<int>
                  --morphologyDilateIterations=<int>
                  --normalizedMaskThreshold=<double>

                Notes:
                  - Tracking options accept kebab-case aliases.
                  - CLI tracking options override values loaded from --tracking-config.
                """;
        System.err.println(usage);
    }

    private record EvaluationOptions(
            Path videoPath,
            Path truthEventsCsv,
            String outputPrefix,
            String cellType,
            String substrate,
            String flow,
            double matchWindowSeconds,
            Double fpsOverride,
            Path metricsOutputPath,
            TrackingConfiguration trackingConfiguration,
            Path scoreBaselineConfigPath) {

        private static EvaluationOptions parse(String[] args) {
            ParseResult parsed = parseArgs(args);
            if (!parsed.positional().isEmpty()) {
                throw new IllegalArgumentException("Unexpected positional arguments: " + parsed.positional());
            }

            String video = findOptionRequired(parsed.options(), "video");
            String truthEvents = findOptionRequired(parsed.options(), "truthEvents");
            String outputPrefix = findOptionRequired(parsed.options(), "outputPrefix");

            String cellType = findOption(parsed.options(), "cellType");
            String substrate = findOption(parsed.options(), "substrate");
            String flow = findOption(parsed.options(), "flow");
            String matchWindowRaw = findOption(parsed.options(), "matchWindowSec");
            String fpsOverrideRaw = findOption(parsed.options(), "fps");
            String metricsOutRaw = findOption(parsed.options(), "metricsOut");
            String scoreBaselineRaw = findOption(parsed.options(), "scoreBaselineConfig");
            if (scoreBaselineRaw == null || scoreBaselineRaw.isBlank()) {
                scoreBaselineRaw = findOption(parsed.options(), "baselineTrackingConfig");
            }

            TrackingConfigurationBuilder trackingBuilder =
                    new TrackingConfigurationBuilder(TrackingConfiguration.defaults());

            String configPathValue = findOption(parsed.options(), "trackingConfig");
            if (configPathValue != null && !configPathValue.isBlank()) {
                applyConfigFile(trackingBuilder, Path.of(configPathValue.trim()));
            }

            applyTrackingOptions(trackingBuilder, parsed.options());

            double matchWindow = 1.0;
            if (matchWindowRaw != null && !matchWindowRaw.isBlank()) {
                try {
                    matchWindow = Double.parseDouble(matchWindowRaw);
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("Invalid value for --match-window-sec: " + matchWindowRaw);
                }
            }
            if (matchWindow <= 0.0) {
                throw new IllegalArgumentException("--match-window-sec must be > 0");
            }

            Double fpsOverride = null;
            if (fpsOverrideRaw != null && !fpsOverrideRaw.isBlank()) {
                try {
                    fpsOverride = Double.parseDouble(fpsOverrideRaw);
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("Invalid value for --fps: " + fpsOverrideRaw);
                }
                if (fpsOverride <= 0.0) {
                    throw new IllegalArgumentException("--fps must be > 0");
                }
            }

            Path metricsOut = metricsOutRaw == null || metricsOutRaw.isBlank()
                    ? Path.of(outputPrefix + "_evaluation_metrics.csv")
                    : Path.of(metricsOutRaw.trim());
            Path scoreBaselineConfigPath = null;
            if (scoreBaselineRaw != null && !scoreBaselineRaw.isBlank()) {
                scoreBaselineConfigPath = Path.of(scoreBaselineRaw.trim());
                if (!Files.exists(scoreBaselineConfigPath)) {
                    throw new IllegalArgumentException("Baseline tracking config file does not exist: " + scoreBaselineConfigPath);
                }
            }

            return new EvaluationOptions(
                    Path.of(video),
                    Path.of(truthEvents),
                    outputPrefix,
                    cellType == null ? "" : cellType,
                    substrate == null ? "" : substrate,
                    flow == null ? "" : flow,
                    matchWindow,
                    fpsOverride,
                    metricsOut,
                    trackingBuilder.build().normalized(),
                    scoreBaselineConfigPath);
        }

        private static ParseResult parseArgs(String[] args) {
            List<String> positional = new ArrayList<>();
            Map<String, String> options = new LinkedHashMap<>();

            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if (!arg.startsWith("--")) {
                    if (arg.isBlank()) {
                        continue;
                    }
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

        private static String findOptionRequired(Map<String, String> options, String canonicalName) {
            String value = findOption(options, canonicalName);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Missing required option --" + canonicalName);
            }
            return value;
        }

        private static String findOption(Map<String, String> options, String canonicalName) {
            String target = normalizeKey(canonicalName);
            for (Map.Entry<String, String> entry : options.entrySet()) {
                if (target.equals(normalizeKey(entry.getKey()))) {
                    return entry.getValue();
                }
            }
            return null;
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

        private static void applyConfigFile(TrackingConfigurationBuilder builder, Path filePath) {
            if (!Files.exists(filePath)) {
                throw new IllegalArgumentException("Tracking config file does not exist: " + filePath);
            }

            Properties properties = new Properties();
            try (Reader reader = Files.newBufferedReader(filePath)) {
                properties.load(reader);
            } catch (IOException ex) {
                throw new IllegalArgumentException("Cannot read tracking config file: " + filePath
                        + " (" + ex.getMessage() + ")");
            }

            for (String key : properties.stringPropertyNames()) {
                builder.apply(key, properties.getProperty(key));
            }
        }

        private static void applyTrackingOptions(TrackingConfigurationBuilder builder, Map<String, String> options) {
            for (Map.Entry<String, String> entry : options.entrySet()) {
                String normalized = normalizeKey(entry.getKey());
                if (isEvaluatorSpecificOption(normalized)) {
                    continue;
                }
                builder.apply(entry.getKey(), entry.getValue());
            }
        }

        private static boolean isEvaluatorSpecificOption(String normalizedKey) {
            return "video".equals(normalizedKey)
                    || "truthevents".equals(normalizedKey)
                    || "outputprefix".equals(normalizedKey)
                    || "celltype".equals(normalizedKey)
                    || "substrate".equals(normalizedKey)
                    || "flow".equals(normalizedKey)
                    || "matchwindowsec".equals(normalizedKey)
                    || "fps".equals(normalizedKey)
                    || "metricsout".equals(normalizedKey)
                    || "scorebaselineconfig".equals(normalizedKey)
                    || "baselinetrackingconfig".equals(normalizedKey)
                    || "trackingconfig".equals(normalizedKey);
        }
    }

    private record ParseResult(List<String> positional, Map<String, String> options) {
    }

    private static final class TrackingConfigurationBuilder {
        private int maxFramesDisappeared;
        private double minContourArea;
        private double maxRectCircumference;
        private double maxVerticalDisplacementPixels;
        private double minHorizontalMovementPixels;
        private double maxAssociationDistancePixels;
        private int mog2HistoryFrames;
        private double mog2VarThreshold;
        private boolean mog2DetectShadows;
        private int morphologyKernelSize;
        private int morphologyOpenIterations;
        private int morphologyDilateIterations;
        private double normalizedMaskThreshold;

        private TrackingConfigurationBuilder(TrackingConfiguration defaults) {
            this.maxFramesDisappeared = defaults.getMaxFramesDisappeared();
            this.minContourArea = defaults.getMinContourArea();
            this.maxRectCircumference = defaults.getMaxRectCircumference();
            this.maxVerticalDisplacementPixels = defaults.getMaxVerticalDisplacementPixels();
            this.minHorizontalMovementPixels = defaults.getMinHorizontalMovementPixels();
            this.maxAssociationDistancePixels = defaults.getMaxAssociationDistancePixels();
            this.mog2HistoryFrames = defaults.getMog2HistoryFrames();
            this.mog2VarThreshold = defaults.getMog2VarThreshold();
            this.mog2DetectShadows = defaults.isMog2DetectShadows();
            this.morphologyKernelSize = defaults.getMorphologyKernelSize();
            this.morphologyOpenIterations = defaults.getMorphologyOpenIterations();
            this.morphologyDilateIterations = defaults.getMorphologyDilateIterations();
            this.normalizedMaskThreshold = defaults.getNormalizedMaskThreshold();
        }

        private void apply(String rawKey, String rawValue) {
            String key = EvaluationOptions.normalizeKey(rawKey);
            String value = rawValue == null ? "" : rawValue.trim();
            if (value.isEmpty()) {
                throw new IllegalArgumentException("Missing value for tracking option: " + rawKey);
            }

            try {
                switch (key) {
                    case "maxframesdisappeared" -> maxFramesDisappeared = Integer.parseInt(value);
                    case "mincontourarea" -> minContourArea = Double.parseDouble(value);
                    case "maxrectcircumference" -> maxRectCircumference = Double.parseDouble(value);
                    case "maxverticaldisplacementpixels", "maxverticaldisplacement" ->
                            maxVerticalDisplacementPixels = Double.parseDouble(value);
                    case "minhorizontalmovementpixels", "minhorizontalmovement" ->
                            minHorizontalMovementPixels = Double.parseDouble(value);
                    case "maxassociationdistancepixels", "maxassociationdistance" ->
                            maxAssociationDistancePixels = Double.parseDouble(value);
                    case "mog2historyframes" -> mog2HistoryFrames = Integer.parseInt(value);
                    case "mog2varthreshold" -> mog2VarThreshold = Double.parseDouble(value);
                    case "mog2detectshadows" -> mog2DetectShadows = parseBoolean(value, rawKey);
                    case "morphologykernelsize" -> morphologyKernelSize = Integer.parseInt(value);
                    case "morphologyopeniterations" -> morphologyOpenIterations = Integer.parseInt(value);
                    case "morphologydilateiterations" -> morphologyDilateIterations = Integer.parseInt(value);
                    case "normalizedmaskthreshold", "maskthreshold" -> normalizedMaskThreshold = Double.parseDouble(value);
                    default -> throw new IllegalArgumentException("Unknown tracking option: " + rawKey);
                }
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid numeric value for " + rawKey + ": " + value);
            }
        }

        private TrackingConfiguration build() {
            return new TrackingConfiguration(
                    maxFramesDisappeared,
                    minContourArea,
                    maxRectCircumference,
                    maxVerticalDisplacementPixels,
                    minHorizontalMovementPixels,
                    maxAssociationDistancePixels,
                    mog2HistoryFrames,
                    mog2VarThreshold,
                    mog2DetectShadows,
                    morphologyKernelSize,
                    morphologyOpenIterations,
                    morphologyDilateIterations,
                    normalizedMaskThreshold);
        }

        private static boolean parseBoolean(String value, String keyName) {
            String normalized = value.trim().toLowerCase();
            return switch (normalized) {
                case "true", "1", "yes", "y", "on" -> true;
                case "false", "0", "no", "n", "off" -> false;
                default -> throw new IllegalArgumentException("Invalid boolean value for " + keyName + ": " + value);
            };
        }
    }
}
