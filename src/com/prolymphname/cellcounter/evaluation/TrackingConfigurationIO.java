package com.prolymphname.cellcounter.evaluation;

import com.prolymphname.cellcounter.trackingadapter.TrackingConfiguration;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

public final class TrackingConfigurationIO {
    private TrackingConfigurationIO() {
    }

    public static TrackingConfiguration loadFromProperties(Path filePath) {
        if (!Files.exists(filePath)) {
            throw new IllegalArgumentException("Tracking config file does not exist: " + filePath);
        }

        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(filePath)) {
            properties.load(reader);
        } catch (IOException ex) {
            throw new IllegalArgumentException(
                    "Cannot read tracking config file: " + filePath + " (" + ex.getMessage() + ")");
        }

        Builder builder = new Builder(TrackingConfiguration.defaults());
        for (String key : properties.stringPropertyNames()) {
            builder.apply(key, properties.getProperty(key));
        }
        return builder.build().normalized();
    }

    public static void saveToProperties(Path filePath, TrackingConfiguration config, String comment) throws IOException {
        Path parent = filePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Properties props = toProperties(config);
        try (Writer writer = Files.newBufferedWriter(filePath)) {
            props.store(writer, comment == null ? "tracking configuration" : comment);
        }
    }

    public static Properties toProperties(TrackingConfiguration config) {
        Properties properties = new Properties();
        properties.setProperty("maxFramesDisappeared", Integer.toString(config.getMaxFramesDisappeared()));
        properties.setProperty("minContourArea", formatDouble(config.getMinContourArea()));
        properties.setProperty("maxRectCircumference", formatDouble(config.getMaxRectCircumference()));
        properties.setProperty("maxVerticalDisplacementPixels", formatDouble(config.getMaxVerticalDisplacementPixels()));
        properties.setProperty("minHorizontalMovementPixels", formatDouble(config.getMinHorizontalMovementPixels()));
        properties.setProperty("maxAssociationDistancePixels", formatDouble(config.getMaxAssociationDistancePixels()));
        properties.setProperty("mog2HistoryFrames", Integer.toString(config.getMog2HistoryFrames()));
        properties.setProperty("mog2VarThreshold", formatDouble(config.getMog2VarThreshold()));
        properties.setProperty("mog2DetectShadows", Boolean.toString(config.isMog2DetectShadows()));
        properties.setProperty("morphologyKernelSize", Integer.toString(config.getMorphologyKernelSize()));
        properties.setProperty("morphologyOpenIterations", Integer.toString(config.getMorphologyOpenIterations()));
        properties.setProperty("morphologyDilateIterations", Integer.toString(config.getMorphologyDilateIterations()));
        properties.setProperty("normalizedMaskThreshold", formatDouble(config.getNormalizedMaskThreshold()));
        return properties;
    }

    public static String canonicalKey(TrackingConfiguration config) {
        return config.getMaxFramesDisappeared() + "|"
                + formatDouble(config.getMinContourArea()) + "|"
                + formatDouble(config.getMaxRectCircumference()) + "|"
                + formatDouble(config.getMaxVerticalDisplacementPixels()) + "|"
                + formatDouble(config.getMinHorizontalMovementPixels()) + "|"
                + formatDouble(config.getMaxAssociationDistancePixels()) + "|"
                + config.getMog2HistoryFrames() + "|"
                + formatDouble(config.getMog2VarThreshold()) + "|"
                + config.isMog2DetectShadows() + "|"
                + config.getMorphologyKernelSize() + "|"
                + config.getMorphologyOpenIterations() + "|"
                + config.getMorphologyDilateIterations() + "|"
                + formatDouble(config.getNormalizedMaskThreshold());
    }

    public static Builder builderFromDefaults() {
        return new Builder(TrackingConfiguration.defaults());
    }

    private static String formatDouble(double value) {
        double rounded = Math.rint(value);
        if (Math.abs(value - rounded) < 1.0e-9) {
            return Long.toString((long) rounded);
        }
        return String.format(Locale.US, "%.6f", value);
    }

    public static String normalizeKey(String key) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                builder.append(Character.toLowerCase(c));
            }
        }
        return builder.toString();
    }

    public static final class Builder {
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

        public Builder(TrackingConfiguration defaults) {
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

        public Builder apply(String rawKey, String rawValue) {
            String key = normalizeKey(rawKey);
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
            return this;
        }

        public TrackingConfiguration build() {
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
