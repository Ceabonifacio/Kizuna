package com.kizuna.service;

import com.kizuna.model.Models.Club;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RecommendationEngine {
    public enum NodeType { STUDENT, INTEREST, CLUB }
    public record NodeKey(NodeType type, long id) { }
    private record QueueEntry(NodeKey node, int depth) { }
    public record ScoredClub(Club club, int score, Set<Long> matchedInterestIds) { }

    public List<ScoredClub> score(long studentId, List<Club> clubs, Map<Long, Integer> userInterests,
                                  Map<Long, Set<Long>> clubInterests) {
        Map<NodeKey, List<NodeKey>> adjacency = new HashMap<>();
        NodeKey student = new NodeKey(NodeType.STUDENT, studentId);
        for (Map.Entry<Long, Integer> interest : userInterests.entrySet()) {
            if (interest.getValue() <= 0) continue;
            connect(adjacency, student, new NodeKey(NodeType.INTEREST, interest.getKey()));
        }
        for (Map.Entry<Long, Set<Long>> club : clubInterests.entrySet()) {
            NodeKey clubNode = new NodeKey(NodeType.CLUB, club.getKey());
            for (Long interestId : club.getValue()) connect(adjacency, new NodeKey(NodeType.INTEREST, interestId), clubNode);
        }

        Map<Long, Set<Long>> matched = new HashMap<>();
        Set<NodeKey> visited = new HashSet<>();
        ArrayDeque<QueueEntry> queue = new ArrayDeque<>();
        visited.add(student);
        queue.add(new QueueEntry(student, 0));
        while (!queue.isEmpty()) {
            QueueEntry current = queue.removeFirst();
            if (current.depth() >= 2) continue;
            for (NodeKey next : adjacency.getOrDefault(current.node(), List.of())) {
                if (current.node().type() == NodeType.INTEREST && next.type() == NodeType.CLUB) {
                    matched.computeIfAbsent(next.id(), ignored -> new HashSet<>()).add(current.node().id());
                }
                if (visited.add(next)) queue.addLast(new QueueEntry(next, current.depth() + 1));
            }
        }

        List<ScoredClub> result = new ArrayList<>();
        for (Club club : clubs) {
            Set<Long> hits = matched.getOrDefault(club.id(), Set.of());
            int score = hits.stream().mapToInt(id -> userInterests.getOrDefault(id, 0)).sum();
            result.add(new ScoredClub(club, score, Set.copyOf(hits)));
        }
        result.sort(Comparator.comparingInt(ScoredClub::score).reversed()
                .thenComparing(item -> item.club().name(), String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(result);
    }

    private static void connect(Map<NodeKey, List<NodeKey>> adjacency, NodeKey from, NodeKey to) {
        adjacency.computeIfAbsent(from, ignored -> new ArrayList<>()).add(to);
    }
}
