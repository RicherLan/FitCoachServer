package com.lanprojects.fitcoach.trainingrecord.dto;

import com.lanprojects.fitcoach.trainingrecord.entity.AiTrainingSummaryConverter;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AiTrainingSummaryTest {
    private AiTrainingSummary valid() {
        var summary = new AiTrainingSummary();
        summary.setExerciseKey("SQUAT"); summary.setModel("mediapipe-3d");
        var set = new AiTrainingSummary.SetSummary();
        set.setRecognizedReps(12); set.setConfirmedReps(10); set.setValidFrameRatio(.9);
        set.setActiveMs(24000); set.setMeanRepMs(2000.0); set.setRangeDegrees(85.0);
        set.setFeedback(List.of("squat_depth")); summary.setSets(List.of(set));
        return summary;
    }
    @Test void correctedCountAndOriginalMeasurementsSurviveJsonRoundTrip() {
        var converter = new AiTrainingSummaryConverter(); var summary = valid();
        assertEquals(summary, converter.convertToEntityAttribute(converter.convertToDatabaseColumn(summary)));
        assertNull(converter.convertToEntityAttribute(null));
        assertNull(converter.convertToDatabaseColumn(null));
    }
    @Test void summaryMustMatchTheFinalRecordButKeepsTheOriginalCount() {
        var request = new TrainingRecordRequest(); request.setAiSummary(valid());
        var exercise = new TrainingRecordRequest.ExerciseItem(); exercise.setExerciseKey("SQUAT");
        var set = new TrainingRecordRequest.SetItem(); set.setReps(10); set.setWeightKg(0.0);
        exercise.setSets(List.of(set)); request.setExercises(List.of(exercise));
        assertTrue(request.isAiSummaryConsistent());
        set.setReps(12); assertFalse(request.isAiSummaryConsistent());
        set.setReps(10); exercise.setExerciseKey("PUSH_UP"); assertFalse(request.isAiSummaryConsistent());
    }
    @Test void nestedValidationRejectsInvalidFramesAndCounts() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();var summary = valid();
            assertTrue(validator.validate(summary).isEmpty());
            summary.getSets().get(0).setValidFrameRatio(1.1);
            assertFalse(validator.validate(summary).isEmpty());
            summary.getSets().get(0).setValidFrameRatio(.9);
            summary.getSets().get(0).setConfirmedReps(0);
            assertFalse(validator.validate(summary).isEmpty());
            summary.getSets().get(0).setConfirmedReps(10);
            summary.setModel("unknown-model");
            assertFalse(validator.validate(summary).isEmpty());
        }
    }
}
