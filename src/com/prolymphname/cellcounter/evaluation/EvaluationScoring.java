package com.prolymphname.cellcounter.evaluation;

public final class EvaluationScoring {
    private static final double MAX_RATIO_CAP = 3.0;
    private static final double EPS = 1e-9;

    private EvaluationScoring() {
    }

    public static double score(
            GroundTruthEvaluator.EvaluationResult reference,
            GroundTruthEvaluator.EvaluationResult candidate) {
        double f1 = bounded(candidate.f1(), 0.0, 1.0);
        double timeGain = improvementRatio(reference.wassersteinTimeSec(), candidate.wassersteinTimeSec());
        double velocityGain = improvementRatio(
                reference.wassersteinVelocityPxPerSec(),
                candidate.wassersteinVelocityPxPerSec());
        double maeGain = improvementRatio(reference.maeVelocityPxPerSec(), candidate.maeVelocityPxPerSec());

        return 0.50 * f1 + 0.20 * timeGain + 0.20 * velocityGain + 0.10 * maeGain;
    }

    private static double improvementRatio(double referenceValue, double candidateValue) {
        if (!Double.isFinite(candidateValue) || candidateValue < 0.0) {
            return 0.0;
        }
        if (!Double.isFinite(referenceValue) || referenceValue < EPS) {
            if (candidateValue < EPS) {
                return 1.0;
            }
            return 1.0 / (1.0 + candidateValue);
        }

        double ratio = referenceValue / (candidateValue + EPS);
        return bounded(ratio, 0.0, MAX_RATIO_CAP);
    }

    private static double bounded(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
