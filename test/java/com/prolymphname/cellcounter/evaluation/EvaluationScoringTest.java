package com.prolymphname.cellcounter.evaluation;

import org.junit.Test;

import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EvaluationScoringTest {

    @Test
    public void score_returnsOneForIdenticalReferenceAndCandidate() {
        GroundTruthEvaluator.EvaluationResult reference = sampleResult(1.0, 0.4, 10.0, 100.0);

        double score = EvaluationScoring.score(reference, reference);

        assertEquals(1.0, score, 0.0001);
    }

    @Test
    public void score_penalizesWorseCandidate() {
        GroundTruthEvaluator.EvaluationResult reference = sampleResult(0.90, 0.4, 10.0, 100.0);
        GroundTruthEvaluator.EvaluationResult candidate = sampleResult(0.60, 1.2, 25.0, 250.0);

        double score = EvaluationScoring.score(reference, candidate);

        assertTrue(score < 1.0);
        assertTrue(score >= 0.0);
    }

    private GroundTruthEvaluator.EvaluationResult sampleResult(
            double f1,
            double w1Time,
            double w1Velocity,
            double maeVelocity) {
        return new GroundTruthEvaluator.EvaluationResult(
                Path.of("truth.csv"),
                Path.of("analysis.csv"),
                10,
                10,
                9,
                1,
                1,
                0.90,
                0.90,
                f1,
                w1Time,
                w1Velocity,
                maeVelocity,
                1.0,
                30.0,
                8);
    }
}
