package com.prolymphname.cellcounter.evaluation;

public final class GaFitnessScoring {
    private static final double MAX_RATIO_CAP = 3.0;
    private static final double EPS = 1e-9;

    private GaFitnessScoring() {
    }

    public static double score(
            GroundTruthEvaluator.EvaluationResult baseline,
            GroundTruthEvaluator.EvaluationResult candidate) {
        double f1 = bounded(candidate.f1(), 0.0, 1.0);
        double timeGain = improvementRatio(baseline.wassersteinTimeSec(), candidate.wassersteinTimeSec());
        double velocityGain = improvementRatio(
                baseline.wassersteinVelocityPxPerSec(),
                candidate.wassersteinVelocityPxPerSec());
        double maeGain = improvementRatio(baseline.maeVelocityPxPerSec(), candidate.maeVelocityPxPerSec());

        return 0.50 * f1 + 0.20 * timeGain + 0.20 * velocityGain + 0.10 * maeGain;
    }

    private static double improvementRatio(double baselineValue, double candidateValue) {
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

    private static double bounded(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
