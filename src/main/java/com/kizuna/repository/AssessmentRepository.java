package com.kizuna.repository;

import com.kizuna.model.Models.Club;
import com.kizuna.model.Models.Option;
import com.kizuna.model.Models.Question;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AssessmentRepository {
    public record Catalog(List<Club> clubs, Map<Long, String> interestNames, Map<Long, Set<Long>> clubInterestIds) { }

    public List<Question> questions(Connection c) throws SQLException {
        Map<Long, QuestionBuilder> questions = new LinkedHashMap<>();
        String sql = "SELECT q.id,q.question_key,q.prompt,o.id AS option_id,o.option_key,o.label FROM assessment_questions q "
                + "LEFT JOIN assessment_options o ON o.question_id=q.id WHERE q.is_active=1 ORDER BY q.sort_order,o.id";
        try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                long id = rs.getLong("id");
                QuestionBuilder q = questions.get(id);
                if (q == null) {
                    q = new QuestionBuilder(id, rs.getString("question_key"), rs.getString("prompt"));
                    questions.put(id, q);
                }
                long optionId = rs.getLong("option_id");
                if (!rs.wasNull()) q.options.add(new Option(optionId, rs.getString("option_key"), rs.getString("label")));
            }
        }
        List<Question> result = new ArrayList<>();
        for (QuestionBuilder question : questions.values()) result.add(new Question(question.id, question.key, question.prompt, List.copyOf(question.options)));
        return result;
    }

    public Map<Long, Integer> mappings(Connection c, long questionId, long optionId) throws SQLException {
        Map<Long, Integer> result = new LinkedHashMap<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT interest_id,weight FROM assessment_option_interests WHERE option_id=? AND EXISTS (SELECT 1 FROM assessment_options WHERE id=? AND question_id=?) ORDER BY interest_id")) {
            ps.setLong(1, optionId); ps.setLong(2, optionId); ps.setLong(3, questionId);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) result.put(rs.getLong(1), rs.getInt(2)); }
        }
        return result;
    }

    public boolean optionBelongsToQuestion(Connection c, long optionId, long questionId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM assessment_options WHERE id=? AND question_id=?")) {
            ps.setLong(1, optionId); ps.setLong(2, questionId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    public void replaceAnswers(Connection c, long userId, Map<Long, Long> answers, long now) throws SQLException {
        try (PreparedStatement delete = c.prepareStatement("DELETE FROM assessment_answers WHERE user_id=?")) { delete.setLong(1, userId); delete.executeUpdate(); }
        try (PreparedStatement insert = c.prepareStatement("INSERT INTO assessment_answers(user_id,question_id,option_id,answered_at_ms) VALUES(?,?,?,?)")) {
            for (Map.Entry<Long, Long> answer : answers.entrySet()) {
                insert.setLong(1, userId); insert.setLong(2, answer.getKey()); insert.setLong(3, answer.getValue()); insert.setLong(4, now); insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    public void replaceUserInterests(Connection c, long userId, Map<Long, Integer> interests, long now) throws SQLException {
        try (PreparedStatement delete = c.prepareStatement("DELETE FROM user_interests WHERE user_id=?")) { delete.setLong(1, userId); delete.executeUpdate(); }
        try (PreparedStatement insert = c.prepareStatement("INSERT INTO user_interests(user_id,interest_id,score,updated_at_ms) VALUES(?,?,?,?)")) {
            for (Map.Entry<Long, Integer> interest : interests.entrySet()) {
                if (interest.getValue() <= 0) continue;
                insert.setLong(1, userId); insert.setLong(2, interest.getKey()); insert.setInt(3, interest.getValue()); insert.setLong(4, now); insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    public Map<Long, Integer> userInterests(Connection c, long userId) throws SQLException {
        Map<Long, Integer> result = new LinkedHashMap<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT interest_id,score FROM user_interests WHERE user_id=? AND score>0 ORDER BY interest_id")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) result.put(rs.getLong(1), rs.getInt(2)); }
        }
        return result;
    }

    public Catalog catalog(Connection c) throws SQLException {
        List<Club> clubs = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT id,name,description FROM clubs ORDER BY name COLLATE NOCASE"); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) clubs.add(new Club(rs.getLong(1), rs.getString(2), rs.getString(3)));
        }
        Map<Long, String> interestNames = new LinkedHashMap<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT id,name FROM interests ORDER BY name COLLATE NOCASE"); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) interestNames.put(rs.getLong(1), rs.getString(2));
        }
        Map<Long, Set<Long>> clubInterests = new HashMap<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT club_id,interest_id FROM club_interests ORDER BY club_id,interest_id"); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) clubInterests.computeIfAbsent(rs.getLong(1), ignored -> new LinkedHashSet<>()).add(rs.getLong(2));
        }
        return new Catalog(List.copyOf(clubs), Map.copyOf(interestNames), Map.copyOf(clubInterests));
    }

    public void replaceScores(Connection c, long userId, List<com.kizuna.service.RecommendationEngine.ScoredClub> scores, long now) throws SQLException {
        try (PreparedStatement delete = c.prepareStatement("DELETE FROM assessment_scores WHERE user_id=?")) { delete.setLong(1, userId); delete.executeUpdate(); }
        try (PreparedStatement insert = c.prepareStatement("INSERT INTO assessment_scores(user_id,club_id,score,calculated_at_ms) VALUES(?,?,?,?)")) {
            for (var score : scores) {
                insert.setLong(1, userId); insert.setLong(2, score.club().id()); insert.setInt(3, score.score()); insert.setLong(4, now); insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    public void markComplete(Connection c, long userId, long now) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE users SET assessment_completed_at_ms=? WHERE id=? AND is_active=1")) {
            ps.setLong(1, now); ps.setLong(2, userId);
            if (ps.executeUpdate() != 1) throw new IllegalStateException("The account is unavailable.");
        }
    }

    public Set<Long> activeMembershipClubs(Connection c, long userId) throws SQLException {
        Set<Long> result = new LinkedHashSet<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT club_id FROM club_memberships WHERE user_id=? AND status='ACTIVE'")) {
            ps.setLong(1, userId); try (ResultSet rs = ps.executeQuery()) { while (rs.next()) result.add(rs.getLong(1)); }
        }
        return result;
    }

    public Set<Long> activeApplicationClubs(Connection c, long userId) throws SQLException {
        Set<Long> result = new LinkedHashSet<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT club_id FROM club_applications WHERE applicant_id=? AND status IN ('PENDING','INTERVIEW_SCHEDULED','INTERVIEW_CONFIRMED')")) {
            ps.setLong(1, userId); try (ResultSet rs = ps.executeQuery()) { while (rs.next()) result.add(rs.getLong(1)); }
        }
        return result;
    }

    public long assessmentCompletedAt(Connection c, long userId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT assessment_completed_at_ms FROM users WHERE id=?")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) { if (!rs.next() || rs.getObject(1) == null) return 0; return rs.getLong(1); }
        }
    }

    public Map<Long, Integer> persistedScores(Connection c, long userId) throws SQLException {
        Map<Long, Integer> result = new LinkedHashMap<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT club_id,score FROM assessment_scores WHERE user_id=?")) {
            ps.setLong(1, userId); try (ResultSet rs = ps.executeQuery()) { while (rs.next()) result.put(rs.getLong(1), rs.getInt(2)); }
        }
        return result;
    }

    private static final class QuestionBuilder {
        final long id; final String key; final String prompt; final List<Option> options = new ArrayList<>();
        QuestionBuilder(long id, String key, String prompt) { this.id = id; this.key = key; this.prompt = prompt; }
    }
}
