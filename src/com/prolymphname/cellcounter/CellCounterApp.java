package com.prolymphname.cellcounter;

import com.prolymphname.cellcounter.application.CellCounterApplicationService;
import com.prolymphname.cellcounter.trackingadapter.TrackingConfiguration;
import org.opencv.core.Core;

import javax.swing.*;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public final class CellCounterApp {
    private CellCounterApp() {
    }

    public static void main(String[] args) {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
        if (args.length > 0) {
            runHeadless(args);
            return;
        }

        configureLookAndFeel();
        SwingUtilities.invokeLater(() -> new CellCounterGUI(new CellCounterApplicationService()).setVisible(true));
    }

    private static void runHeadless(String[] args) {
        try {
            HeadlessOptions options = HeadlessOptions.parse(args);
            CellCounterApplicationService appService = new CellCounterApplicationService();
            appService.setTrackingConfiguration(options.trackingConfiguration());

            HeadlessProcessor processor = new HeadlessProcessor(appService);
            processor.run(
                    options.inputVideo(),
                    options.outputPrefix(),
                    options.cellType(),
                    options.substrate(),
                    options.flow());
        } catch (IllegalArgumentException ex) {
            System.err.println("Invalid headless arguments: " + ex.getMessage());
            printHeadlessUsage();
        }
    }

    private static void configureLookAndFeel() {
        try {
            boolean nimbusFound = false;
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    nimbusFound = true;
                    break;
                }
            }
            if (!nimbusFound) {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            }
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException
                 | UnsupportedLookAndFeelException e) {
            System.err.println("Failed to set Look and Feel: " + e.getMessage());
        }
    }

    private static void printHeadlessUsage() {
        String usage = """
                Usage:
                  GUI mode:
                    run with no arguments

                  Headless mode:
                    CellCounterApp <inputVideo> <outputPrefix> [cellType substrate flow] [options]

                Headless options:
                  --tracking-config=<file.properties>
                  --cellType=<value> --substrate=<value> --flow=<value>
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
                  - Tracking options can be provided in a properties file and/or CLI.
                  - CLI tracking options override properties file values.
                  - Option names also accept kebab-case variants, e.g. --max-frames-disappeared.
                """;
        System.err.println(usage);
    }

    private record HeadlessOptions(
            String inputVideo,
            String outputPrefix,
            String cellType,
            String substrate,
            String flow,
            TrackingConfiguration trackingConfiguration) {

        private static HeadlessOptions parse(String[] args) {
            ParseResult parsed = parseArgs(args);
            List<String> positional = parsed.positional();
            Map<String, String> options = parsed.options();

            if (positional.size() < 2) {
                throw new IllegalArgumentException("Expected at least <inputVideo> and <outputPrefix>.");
            }
            if (positional.size() > 5) {
                throw new IllegalArgumentException("Too many positional arguments.");
            }

            String inputVideo = positional.get(0);
            String outputPrefix = positional.get(1);
            String cellType = positional.size() >= 3 ? positional.get(2) : "";
            String substrate = positional.size() >= 4 ? positional.get(3) : "";
            String flow = positional.size() >= 5 ? positional.get(4) : "";

            String cellTypeOpt = findOption(options, "cellType");
            String substrateOpt = findOption(options, "substrate");
            String flowOpt = findOption(options, "flow");
            if (cellTypeOpt != null) {
                cellType = cellTypeOpt;
            }
            if (substrateOpt != null) {
                substrate = substrateOpt;
            }
            if (flowOpt != null) {
                flow = flowOpt;
            }

            TrackingConfigurationBuilder builder = new TrackingConfigurationBuilder(TrackingConfiguration.defaults());
            String configPathValue = findOption(options, "trackingConfig");
            if (configPathValue != null && !configPathValue.isBlank()) {
                applyConfigFile(builder, Path.of(configPathValue.trim()));
            }
            applyTrackingOptions(builder, options);

            return new HeadlessOptions(
                    inputVideo,
                    outputPrefix,
                    cellType,
                    substrate,
                    flow,
                    builder.build().normalized());
        }

        private static ParseResult parseArgs(String[] args) {
            List<String> positional = new ArrayList<>();
            Map<String, String> options = new LinkedHashMap<>();

            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
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

        private static void applyConfigFile(TrackingConfigurationBuilder builder, Path filePath) {
            if (!Files.exists(filePath)) {
                throw new IllegalArgumentException("Tracking config file does not exist: " + filePath);
            }
            Properties properties = new Properties();
            try (Reader reader = Files.newBufferedReader(filePath)) {
                properties.load(reader);
            } catch (IOException ex) {
                throw new IllegalArgumentException("Cannot read tracking config file: " + filePath + " (" + ex.getMessage() + ")");
            }

            for (String key : properties.stringPropertyNames()) {
                builder.apply(key, properties.getProperty(key));
            }
        }

        private static void applyTrackingOptions(TrackingConfigurationBuilder builder, Map<String, String> options) {
            for (Map.Entry<String, String> entry : options.entrySet()) {
                String key = entry.getKey();
                String normalized = normalizeKey(key);
                if ("trackingconfig".equals(normalized) || "celltype".equals(normalized)
                        || "substrate".equals(normalized) || "flow".equals(normalized)) {
                    continue;
                }
                builder.apply(key, entry.getValue());
            }
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
            String key = HeadlessOptions.normalizeKey(rawKey);
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
