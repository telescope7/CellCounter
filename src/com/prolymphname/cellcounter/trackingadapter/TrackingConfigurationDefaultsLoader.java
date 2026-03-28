package com.prolymphname.cellcounter.trackingadapter;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

public final class TrackingConfigurationDefaultsLoader {
    public static final String DEFAULT_CONFIG_FILE_NAME = "CellCounter.properties";

    private static volatile TrackingConfiguration cachedDefaults;
    private static volatile Path cachedDefaultsPath;

    private TrackingConfigurationDefaultsLoader() {
    }

    public static TrackingConfiguration loadRequiredDefaults() {
        TrackingConfiguration local = cachedDefaults;
        if (local != null) {
            return local;
        }

        synchronized (TrackingConfigurationDefaultsLoader.class) {
            if (cachedDefaults != null) {
                return cachedDefaults;
            }

            Path resolvedPath = resolveRequiredDefaultConfigPath();
            Properties properties = loadProperties(resolvedPath);
            cachedDefaults = new TrackingConfiguration(
                    parseRequiredInt(properties, resolvedPath, "maxFramesDisappeared"),
                    parseRequiredDouble(properties, resolvedPath, "minContourArea"),
                    parseRequiredDouble(properties, resolvedPath, "maxRectCircumference"),
                    parseRequiredDouble(properties, resolvedPath, "maxVerticalDisplacementPixels"),
                    parseRequiredDouble(properties, resolvedPath, "minHorizontalMovementPixels"),
                    parseRequiredDouble(properties, resolvedPath, "maxAssociationDistancePixels"),
                    parseRequiredInt(properties, resolvedPath, "mog2HistoryFrames"),
                    parseRequiredDouble(properties, resolvedPath, "mog2VarThreshold"),
                    parseRequiredBoolean(properties, resolvedPath, "mog2DetectShadows"),
                    parseRequiredInt(properties, resolvedPath, "morphologyKernelSize"),
                    parseRequiredInt(properties, resolvedPath, "morphologyOpenIterations"),
                    parseRequiredInt(properties, resolvedPath, "morphologyDilateIterations"),
                    parseRequiredDouble(properties, resolvedPath, "normalizedMaskThreshold"),
                    parseRequiredInt(properties, resolvedPath, "confidenceFieldWidthPercent"),
                    parseRequiredTrackerAlgorithm(properties, resolvedPath, "trackerAlgorithm"))
                    .normalized();
            cachedDefaultsPath = resolvedPath;
            return cachedDefaults;
        }
    }

    public static Path resolveRequiredDefaultConfigPath() {
        Path resolved = resolveDefaultConfigPath();
        if (resolved == null) {
            throw new IllegalStateException(
                    "Required default tracking configuration file was not found: " + DEFAULT_CONFIG_FILE_NAME);
        }
        return resolved;
    }

    public static Path getCachedDefaultsPath() {
        Path local = cachedDefaultsPath;
        return local != null ? local : resolveRequiredDefaultConfigPath();
    }

    static void clearCacheForTests() {
        cachedDefaults = null;
        cachedDefaultsPath = null;
    }

    private static Path resolveDefaultConfigPath() {
        List<Path> candidates = new ArrayList<>();
        candidates.add(Path.of(DEFAULT_CONFIG_FILE_NAME));
        candidates.add(Path.of("target", "classes", DEFAULT_CONFIG_FILE_NAME));

        CodeSource codeSource = TrackingConfigurationDefaultsLoader.class.getProtectionDomain().getCodeSource();
        if (codeSource != null && codeSource.getLocation() != null) {
            try {
                Path location = Path.of(codeSource.getLocation().toURI()).toAbsolutePath().normalize();
                Path base = Files.isDirectory(location) ? location : location.getParent();
                if (base != null) {
                    candidates.add(base.resolve(DEFAULT_CONFIG_FILE_NAME));
                    candidates.add(base.resolve("config").resolve(DEFAULT_CONFIG_FILE_NAME));
                    Path parent = base.getParent();
                    if (parent != null) {
                        candidates.add(parent.resolve(DEFAULT_CONFIG_FILE_NAME));
                        candidates.add(parent.resolve("config").resolve(DEFAULT_CONFIG_FILE_NAME));
                    }
                }
            } catch (Exception ignored) {
                // Fall through to existing candidate paths.
            }
        }

        for (Path candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            Path absolute = candidate.toAbsolutePath().normalize();
            if (Files.isRegularFile(absolute)) {
                return absolute;
            }
        }
        return null;
    }

    private static Properties loadProperties(Path path) {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path)) {
            properties.load(reader);
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Unable to read default tracking configuration from " + path.toAbsolutePath()
                            + ": " + ex.getMessage(),
                    ex);
        }
        return properties;
    }

    private static String require(Properties properties, Path path, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Missing required default tracking setting '" + key + "' in " + path.toAbsolutePath());
        }
        return value.trim();
    }

    private static int parseRequiredInt(Properties properties, Path path, String key) {
        String value = require(properties, path, key);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new IllegalStateException(
                    "Invalid integer for default tracking setting '" + key + "' in "
                            + path.toAbsolutePath() + ": " + value,
                    ex);
        }
    }

    private static double parseRequiredDouble(Properties properties, Path path, String key) {
        String value = require(properties, path, key);
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            throw new IllegalStateException(
                    "Invalid numeric value for default tracking setting '" + key + "' in "
                            + path.toAbsolutePath() + ": " + value,
                    ex);
        }
    }

    private static boolean parseRequiredBoolean(Properties properties, Path path, String key) {
        String value = require(properties, path, key).toLowerCase(Locale.ROOT);
        return switch (value) {
            case "true", "1", "yes", "y", "on" -> true;
            case "false", "0", "no", "n", "off" -> false;
            default -> throw new IllegalStateException(
                    "Invalid boolean for default tracking setting '" + key + "' in "
                            + path.toAbsolutePath() + ": " + value);
        };
    }

    private static TrackerAlgorithm parseRequiredTrackerAlgorithm(Properties properties, Path path, String key) {
        String value = require(properties, path, key);
        try {
            return TrackerAlgorithm.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "Invalid tracker algorithm for default tracking setting '" + key + "' in "
                            + path.toAbsolutePath() + ": " + value,
                    ex);
        }
    }
}
