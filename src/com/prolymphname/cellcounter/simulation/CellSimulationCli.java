package com.prolymphname.cellcounter.simulation;

import com.prolymphname.cellcounter.OpenCvSupport;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CellSimulationCli {
    private CellSimulationCli() {
    }

    public static void main(String[] args) throws Exception {
        OpenCvSupport.loadOpenCv();

        Map<String, String> options = parseOptions(args);
        CellSimulationParameters defaults = CellSimulationParameters.defaults();

        CellSimulationParameters params = new CellSimulationParameters(
                parseInt(options, "width", defaults.width()),
                parseInt(options, "height", defaults.height()),
                parseInt(options, "cellCount", defaults.cellCount()),
                parseInt(options, "delay", defaults.delaySeconds()),
                parseInt(options, "fps", defaults.fps()),
                parseInt(options, "length", defaults.lengthSeconds()),
                parseInt(options, "radius", defaults.cellRadiusPx()),
                parseDouble(options, "velocity", defaults.baseVelocityPxPerFrame()),
                parseLong(options, "seed", defaults.randomSeed()),
                options.getOrDefault("outputBase", defaults.outputBaseName()),
                Path.of(options.getOrDefault("outputDir", defaults.outputDirectory().toAbsolutePath().toString()))
        ).normalized();

        CellSimulationGenerator generator = new CellSimulationGenerator();
        CellSimulationGenerator.SimulationResult result = generator.generate(params, (current, total) -> {
            if (total <= 0) {
                return;
            }
            int percent = (int) Math.round((current * 100.0) / total);
            if (percent % 10 == 0) {
                System.out.println("Progress: " + percent + "%");
            }
        });

        System.out.println("Simulation complete.");
        System.out.println("Video: " + result.videoPath().toAbsolutePath());
        System.out.println("Legacy CSV: " + result.legacyCsvPath().toAbsolutePath());
        System.out.println("Events CSV: " + result.eventsCsvPath().toAbsolutePath());
        System.out.println("Trajectory CSV: " + result.trajectoryCsvPath().toAbsolutePath());
        System.out.println("Manifest JSON: " + result.manifestJsonPath().toAbsolutePath());
    }

    private static Map<String, String> parseOptions(String[] args) {
        Map<String, String> options = new LinkedHashMap<>();
        for (String arg : args) {
            if (!arg.startsWith("--") || !arg.contains("=")) {
                continue;
            }
            String[] parts = arg.substring(2).split("=", 2);
            options.put(parts[0], parts[1]);
        }
        return options;
    }

    private static int parseInt(Map<String, String> options, String key, int defaultValue) {
        String value = options.get(key);
        return value == null ? defaultValue : Integer.parseInt(value);
    }

    private static long parseLong(Map<String, String> options, String key, long defaultValue) {
        String value = options.get(key);
        return value == null ? defaultValue : Long.parseLong(value);
    }

    private static double parseDouble(Map<String, String> options, String key, double defaultValue) {
        String value = options.get(key);
        if (value == null) {
            return defaultValue;
        }
        return Double.parseDouble(value);
    }
}
