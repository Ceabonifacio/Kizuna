package com.kizuna;

import com.kizuna.model.Models.Club;
import com.kizuna.service.RecommendationEngine;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RecommendationEngineTest {
    @Test
    void bfsScoresEveryClubCapturesAllSharedInterestsAndUsesDeterministicTies() {
        List<Club> clubs = List.of(
                new Club(1, "Beta", "B"), new Club(2, "alpha", "A"), new Club(3, "Gamma", "G"));
        Map<Long, Integer> studentInterests = Map.of(10L, 2, 11L, 1);
        Map<Long, Set<Long>> clubInterests = Map.of(
                1L, Set.of(10L),
                2L, Set.of(10L, 11L),
                3L, Set.of(77L));

        List<RecommendationEngine.ScoredClub> results = new RecommendationEngine().score(42, clubs, studentInterests, clubInterests);
        assertEquals(List.of("alpha", "Beta", "Gamma"), results.stream().map(item -> item.club().name()).toList());
        assertEquals(List.of(3, 2, 0), results.stream().map(RecommendationEngine.ScoredClub::score).toList());
        assertEquals(Set.of(10L, 11L), results.getFirst().matchedInterestIds());
        assertTrue(results.getLast().matchedInterestIds().isEmpty());
    }

    @Test
    void noPositiveInterestProducesZeroScoreFallbacksWithoutInventedMatches() {
        List<Club> clubs = List.of(new Club(1, "Kendo", "Practice"));
        List<RecommendationEngine.ScoredClub> results = new RecommendationEngine().score(99, clubs, Map.of(), Map.of(1L, Set.of(4L)));
        assertEquals(1, results.size());
        assertEquals(0, results.getFirst().score());
        assertTrue(results.getFirst().matchedInterestIds().isEmpty());
    }
}
