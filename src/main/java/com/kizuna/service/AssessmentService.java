package com.kizuna.service;

import com.kizuna.db.Database;
import com.kizuna.model.Models.AssessmentResult;
import com.kizuna.model.Models.Question;
import com.kizuna.model.Models.Recommendation;
import com.kizuna.model.Models.UserProfile;
import com.kizuna.repository.AssessmentRepository;
import com.kizuna.repository.UserRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class AssessmentService {
    private final Database database;
    private final Session session;
    private final AssessmentRepository assessments;
    private final UserRepository users;
    private final RecommendationEngine engine;

    public AssessmentService(Database database, Session session, AssessmentRepository assessments,
                            UserRepository users, RecommendationEngine engine) {
        this.database = database; this.session = session; this.assessments = assessments; this.users = users; this.engine = engine;
    }

    public List<Question> questions() {
        try { return database.read(assessments::questions); }
        catch (Exception e) { throw new IllegalStateException("Could not load the preference assessment.", e); }
    }

    public AssessmentResult submit(Map<Long, Long> answers) {
        long userId = session.requireUserId();
        if (answers == null) throw new IllegalArgumentException("Answer every question before submitting.");
        try {
            AssessmentResult result = database.transaction(c -> {
                List<Question> questions = assessments.questions(c);
                Set<Long> required = new HashSet<>();
                questions.forEach(q -> required.add(q.id()));
                if (answers.size() != required.size() || !answers.keySet().equals(required)) {
                    throw new IllegalArgumentException("Answer every question before submitting.");
                }
                Map<Long, Integer> interests = new java.util.LinkedHashMap<>();
                for (Question question : questions) {
                    long optionId = answers.get(question.id());
                    if (!assessments.optionBelongsToQuestion(c, optionId, question.id())) {
                        throw new IllegalArgumentException("Choose an available answer for every question.");
                    }
                    Map<Long, Integer> weights = assessments.mappings(c, question.id(), optionId);
                    for (Map.Entry<Long, Integer> entry : weights.entrySet()) interests.merge(entry.getKey(), entry.getValue(), Integer::sum);
                }
                long now = Instant.now().toEpochMilli();
                assessments.replaceAnswers(c, userId, answers, now);
                assessments.replaceUserInterests(c, userId, interests, now);
                AssessmentRepository.Catalog catalog = assessments.catalog(c);
                List<RecommendationEngine.ScoredClub> scores = engine.score(userId, catalog.clubs(), interests, catalog.clubInterestIds());
                assessments.replaceScores(c, userId, scores, now);
                assessments.markComplete(c, userId, now);
                Set<Long> excluded = new HashSet<>(assessments.activeMembershipClubs(c, userId));
                excluded.addAll(assessments.activeApplicationClubs(c, userId));
                return new AssessmentResult(toRecommendations(scores, catalog.interestNames(), excluded).stream().limit(3).toList(), catalog.clubs().size());
            });
            UserProfile profile = database.read(c -> users.findProfile(c, userId));
            session.refresh(profile);
            return result;
        } catch (IllegalArgumentException | SecurityException e) { throw e; }
        catch (Exception e) { throw new IllegalStateException("Could not save your assessment. Your previous results are unchanged.", e); }
    }

    public List<Recommendation> recommendations() {
        long userId = session.requireUserId();
        try {
            return database.read(c -> {
                if (assessments.assessmentCompletedAt(c, userId) == 0) return List.of();
                AssessmentRepository.Catalog catalog = assessments.catalog(c);
                Map<Long, Integer> interests = assessments.userInterests(c, userId);
                List<RecommendationEngine.ScoredClub> calculated = engine.score(userId, catalog.clubs(), interests, catalog.clubInterestIds());
                Map<Long, Integer> persisted = assessments.persistedScores(c, userId);
                List<RecommendationEngine.ScoredClub> current = new ArrayList<>();
                for (RecommendationEngine.ScoredClub item : calculated) {
                    current.add(new RecommendationEngine.ScoredClub(item.club(), persisted.getOrDefault(item.club().id(), 0), item.matchedInterestIds()));
                }
                current.sort(Comparator.comparingInt(RecommendationEngine.ScoredClub::score).reversed()
                        .thenComparing(item -> item.club().name(), String.CASE_INSENSITIVE_ORDER));
                Set<Long> excluded = new HashSet<>(assessments.activeMembershipClubs(c, userId));
                excluded.addAll(assessments.activeApplicationClubs(c, userId));
                return toRecommendations(current, catalog.interestNames(), excluded).stream().limit(3).toList();
            });
        } catch (Exception e) { throw new IllegalStateException("Could not load recommendations.", e); }
    }

    private static List<Recommendation> toRecommendations(List<RecommendationEngine.ScoredClub> scores,
                                                           Map<Long, String> interestNames, Set<Long> excluded) {
        List<Recommendation> result = new ArrayList<>();
        for (RecommendationEngine.ScoredClub item : scores) {
            if (excluded.contains(item.club().id())) continue;
            String reason;
            if (item.score() == 0 || item.matchedInterestIds().isEmpty()) {
                reason = "Match score is 0. Review the club description to see if it interests you.";
            } else {
                TreeSet<String> tags = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
                for (Long id : item.matchedInterestIds()) if (interestNames.containsKey(id)) tags.add(interestNames.get(id));
                reason = "Matches your interests in " + String.join(", ", tags) + ".";
            }
            result.add(new Recommendation(item.club().id(), item.club().name(), item.club().description(), item.score(), reason));
        }
        return List.copyOf(result);
    }
}
