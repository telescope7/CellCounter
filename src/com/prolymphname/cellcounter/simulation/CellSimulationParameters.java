package com.prolymphname.cellcounter.simulation;

import java.nio.file.Path;
import java.nio.file.Paths;

public record CellSimulationParameters(
        int width,
        int height,
        int cellCount,
        int delaySeconds,
        int fps,
        int lengthSeconds,
        int cellRadiusPx,
        double baseVelocityPxPerFrame,
        long randomSeed,
        String outputBaseName,
        Path outputDirectory) {

    public static CellSimulationParameters defaults() {
        return new CellSimulationParameters(
                500,
                500,
                300,
                15,
                30,
                60,
                7,
                7.0,
                42L,
                "simcell",
                Paths.get(System.getProperty("user.dir")));
    }

    public CellSimulationParameters normalized() {
        int normalizedLength = Math.max(1, lengthSeconds);
        int normalizedDelay = Math.max(0, Math.min(delaySeconds, normalizedLength));
        Path normalizedDirectory = outputDirectory == null ? Paths.get(System.getProperty("user.dir")) : outputDirectory;
        String normalizedBase = (outputBaseName == null || outputBaseName.isBlank()) ? "simcell" : outputBaseName.trim();

        return new CellSimulationParameters(
                Math.max(32, width),
                Math.max(32, height),
                Math.max(1, cellCount),
                normalizedDelay,
                Math.max(1, fps),
                normalizedLength,
                Math.max(1, cellRadiusPx),
                Math.max(0.01, baseVelocityPxPerFrame),
                randomSeed,
                normalizedBase,
                normalizedDirectory);
    }
}
