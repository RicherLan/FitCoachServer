package com.lanprojects.fitcoach.challenge;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/** Versioned content. Scores from different rules must never share a leaderboard. */
public final class ChallengeRules {
    private ChallengeRules() {}
    public static final String PROJECT = "pushup";
    public static final String VERSION = "pushup-60-v1";
    public static final String MODEL = "mediapipe-3d";
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    public static final int[][] LEVELS = {{3,3},{4,4},{5,5},{4,4,4},{5,5,4},{5,5,5},
            {6,5,5},{6,6,6},{7,6,6},{7,7,6},{8,7,7},{8,8,8}};
    public static List<Integer> targets(int level, int difficulty) {
        if (level < 1 || level > LEVELS.length || difficulty < 0 || difficulty > 2)
            throw new IllegalArgumentException("level/difficulty");
        double factor = difficulty == 0 ? .5 : difficulty == 2 ? 1.5 : 1;
        return Arrays.stream(LEVELS[level-1]).map(n -> (int)Math.ceil(n * factor)).boxed().toList();
    }
    public static LocalDate week(Instant time) {
        return time.atZone(ZONE).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
    /** Basic plausibility only, not proof that camera events are authentic. */
    public static boolean plausible(List<Long> events, long elapsed) {
        if (events == null || events.size() > 600 || elapsed < 0 || elapsed > 3600000) return false;
        long previous = -1000;
        for (Long event : events) {
            if (event == null || event < 0 || event >= elapsed || event - previous < 400) return false;
            previous = event;
        }
        return true;
    }
}
