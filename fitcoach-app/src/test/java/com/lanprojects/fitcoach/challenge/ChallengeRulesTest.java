package com.lanprojects.fitcoach.challenge;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ChallengeRulesTest {
    @Test void weekUsesShanghaiMondayNotDeviceOrSubmissionDate() {
        assertEquals(LocalDate.of(2026,10,12), ChallengeRules.week(Instant.parse("2026-10-18T15:59:59Z")));
        assertEquals(LocalDate.of(2026,10,19), ChallengeRules.week(Instant.parse("2026-10-18T16:00:00Z")));
    }
    @Test void endIsExclusiveAndEventsMustBeOrderedAndPlausible() {
        assertTrue(ChallengeRules.plausible(List.of(0L,400L,59999L),60000));
        assertFalse(ChallengeRules.plausible(List.of(60000L),60000));
        assertFalse(ChallengeRules.plausible(List.of(3000L,2000L),60000));
        assertFalse(ChallengeRules.plausible(List.of(0L,399L),60000));
        assertFalse(ChallengeRules.plausible(List.of(-1L),60000));
    }
    @Test void contentAndDifficultyHaveStableTargets() {
        assertEquals(List.of(2,2),ChallengeRules.targets(1,0));
        assertEquals(List.of(3,3),ChallengeRules.targets(1,1));
        assertEquals(List.of(5,5),ChallengeRules.targets(1,2));
        assertEquals(List.of(12,12,12),ChallengeRules.targets(12,2));
        assertThrows(IllegalArgumentException.class,()->ChallengeRules.targets(13,1));
        assertThrows(IllegalArgumentException.class,()->ChallengeRules.targets(1,3));
    }
}
