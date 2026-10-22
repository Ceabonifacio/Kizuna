package com.kizuna.db;

import com.kizuna.model.Models.Role;
import com.kizuna.util.PasswordHasher;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DemoSeeder {
    public static final String DEMO_PASSWORD = "KizunaDemo25!";

    private record ClubSeed(String name, String description, List<String> tags) { }
    private record AccountSeed(String username, String name, String year, String club,
                               Role role, boolean memberOnly) { }
    private record InterestSeed(String code, String name) { }
    private record QuestionSeed(String key, String prompt, List<OptionSeed> options) { }
    private record OptionSeed(String key, String label, Map<String, Integer> weights) { }

    private final PasswordHasher hasher;

    public DemoSeeder(PasswordHasher hasher) { this.hasher = hasher; }

    public void seed(Connection connection) throws SQLException {
        seedInterests(connection);
        seedClubs(connection);
        seedQuestions(connection);
        seedAccounts(connection);
        seedMemberships(connection);
        seedSamples(connection);
    }

    private static List<ClubSeed> clubs() {
        return List.of(
                new ClubSeed("Kaminari", "Japanese musical and dance art.", List.of("JAPANESE_CULTURE", "MUSIC", "DANCE", "PERFORMANCE", "ART")),
                new ClubSeed("Peer", "Peer networking and support through fellow club members.", List.of("PEER_SUPPORT", "COMMUNICATION", "NETWORKING", "MENTORSHIP", "COMMUNITY")),
                new ClubSeed("Ichiban", "Music, dance, and art across different themes.", List.of("MUSIC", "DANCE", "ART", "CREATIVITY", "PERFORMANCE")),
                new ClubSeed("Tsuushin", "Journalism and student reporting.", List.of("WRITING", "JOURNALISM", "MEDIA", "COMMUNICATION", "PHOTOGRAPHY")),
                new ClubSeed("Kendo", "Kendo-focused practice and activities.", List.of("MARTIAL_ARTS", "DISCIPLINE", "FITNESS", "TRADITION", "TEAMWORK")),
                new ClubSeed("Red Cross", "First aid and helping people prepare for emergencies.", List.of("FIRST_AID", "VOLUNTEERING", "PUBLIC_SERVICE", "EMERGENCY_PREPAREDNESS", "COMMUNITY"))
        );
    }

    private static List<InterestSeed> interests() {
        return List.of(
                new InterestSeed("JAPANESE_CULTURE", "Japanese culture"), new InterestSeed("MUSIC", "Music"),
                new InterestSeed("DANCE", "Dance"), new InterestSeed("PERFORMANCE", "Performance"),
                new InterestSeed("ART", "Art"), new InterestSeed("PEER_SUPPORT", "Peer support"),
                new InterestSeed("COMMUNICATION", "Communication"), new InterestSeed("NETWORKING", "Networking"),
                new InterestSeed("MENTORSHIP", "Mentorship"), new InterestSeed("COMMUNITY", "Community"),
                new InterestSeed("WRITING", "Writing"), new InterestSeed("JOURNALISM", "Journalism"),
                new InterestSeed("MEDIA", "Media"), new InterestSeed("PHOTOGRAPHY", "Photography"),
                new InterestSeed("MARTIAL_ARTS", "Martial arts"), new InterestSeed("DISCIPLINE", "Discipline"),
                new InterestSeed("FITNESS", "Fitness"), new InterestSeed("TRADITION", "Tradition"),
                new InterestSeed("TEAMWORK", "Teamwork"), new InterestSeed("FIRST_AID", "First aid"),
                new InterestSeed("VOLUNTEERING", "Volunteering"), new InterestSeed("PUBLIC_SERVICE", "Public service"),
                new InterestSeed("EMERGENCY_PREPAREDNESS", "Emergency preparedness"), new InterestSeed("CREATIVITY", "Creativity")
        );
    }

    private static List<QuestionSeed> questions() {
        return List.of(
                new QuestionSeed("campus_activity", "Which campus activity would you most like to try?", List.of(
                        option("arts", "Making music, dance, or visual art", "MUSIC", "DANCE", "ART", "PERFORMANCE"),
                        option("culture", "Exploring Japanese culture and traditions", "JAPANESE_CULTURE", "TRADITION", "ART"),
                        option("support", "Helping peers feel welcome and supported", "PEER_SUPPORT", "MENTORSHIP", "COMMUNITY"),
                        option("reporting", "Writing stories and sharing student news", "WRITING", "JOURNALISM", "MEDIA"),
                        option("practice", "Learning a martial art with a team", "MARTIAL_ARTS", "DISCIPLINE", "FITNESS"),
                        option("service", "Learning first aid and serving the community", "FIRST_AID", "VOLUNTEERING", "PUBLIC_SERVICE", "EMERGENCY_PREPAREDNESS")
                )),
                new QuestionSeed("strength", "What would you like to practice?", List.of(
                        option("create", "Creativity and performance", "CREATIVITY", "PERFORMANCE", "MUSIC"),
                        option("connect", "Communication and mentorship", "COMMUNICATION", "MENTORSHIP", "NETWORKING"),
                        option("focus", "Discipline and teamwork", "DISCIPLINE", "TEAMWORK", "TRADITION"),
                        option("care", "First aid and volunteering", "FIRST_AID", "VOLUNTEERING", "COMMUNITY"),
                        option("write", "Writing and photography", "WRITING", "PHOTOGRAPHY", "MEDIA")
                )),
                new QuestionSeed("group", "Which group setting sounds best?", List.of(
                        option("showcase", "Planning a creative showcase", "ART", "DANCE", "PERFORMANCE"),
                        option("conversation", "Small conversations with other students", "PEER_SUPPORT", "COMMUNICATION", "NETWORKING"),
                        option("practice_group", "Regular physical practice with teammates", "MARTIAL_ARTS", "FITNESS", "TEAMWORK"),
                        option("response", "Preparing together to help during emergencies", "EMERGENCY_PREPAREDNESS", "PUBLIC_SERVICE", "FIRST_AID")
                )),
                new QuestionSeed("free_time", "How do you like spending free time?", List.of(
                        option("stage", "Watching or making a performance", "MUSIC", "DANCE", "PERFORMANCE"),
                        option("culture_time", "Learning about culture and history", "JAPANESE_CULTURE", "TRADITION", "WRITING"),
                        option("community_time", "Meeting people and building community", "COMMUNITY", "NETWORKING", "MENTORSHIP"),
                        option("news", "Following campus stories and media", "JOURNALISM", "MEDIA", "PHOTOGRAPHY"),
                        option("service_time", "Taking part in a service activity", "VOLUNTEERING", "PUBLIC_SERVICE", "FIRST_AID")
                ))
        );
    }

    private static OptionSeed option(String key, String label, String... codes) {
        Map<String, Integer> weights = new LinkedHashMap<>();
        for (String code : codes) weights.put(code, 1);
        return new OptionSeed(key, label, weights);
    }

    private void seedInterests(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO interests(code,name) VALUES(?,?) ON CONFLICT(code) DO NOTHING")) {
            for (InterestSeed interest : interests()) {
                ps.setString(1, interest.code()); ps.setString(2, interest.name()); ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void seedClubs(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO clubs(name,description) VALUES(?,?) ON CONFLICT(name) DO NOTHING")) {
            for (ClubSeed club : clubs()) { ps.setString(1, club.name()); ps.setString(2, club.description()); ps.addBatch(); }
            ps.executeBatch();
        }
        try (PreparedStatement selectClub = c.prepareStatement("SELECT id FROM clubs WHERE name=?");
             PreparedStatement selectInterest = c.prepareStatement("SELECT id FROM interests WHERE code=?");
             PreparedStatement mapping = c.prepareStatement("INSERT INTO club_interests(club_id,interest_id) VALUES(?,?) ON CONFLICT DO NOTHING")) {
            for (ClubSeed club : clubs()) {
                long clubId = findId(selectClub, club.name());
                for (String code : club.tags()) {
                    long interestId = findId(selectInterest, code);
                    mapping.setLong(1, clubId); mapping.setLong(2, interestId); mapping.addBatch();
                }
            }
            mapping.executeBatch();
        }
    }

    private void seedQuestions(Connection c) throws SQLException {
        try (PreparedStatement insertQ = c.prepareStatement("INSERT INTO assessment_questions(question_key,prompt,sort_order,is_active) VALUES(?,?,?,1) ON CONFLICT(question_key) DO NOTHING");
             PreparedStatement selectQ = c.prepareStatement("SELECT id FROM assessment_questions WHERE question_key=?");
             PreparedStatement insertOption = c.prepareStatement("INSERT INTO assessment_options(question_id,option_key,label) VALUES(?,?,?) ON CONFLICT(question_id,option_key) DO NOTHING");
             PreparedStatement selectOption = c.prepareStatement("SELECT id FROM assessment_options WHERE question_id=? AND option_key=?");
             PreparedStatement insertMap = c.prepareStatement("INSERT INTO assessment_option_interests(option_id,interest_id,weight) VALUES(?,?,?) ON CONFLICT DO NOTHING");
             PreparedStatement selectInterest = c.prepareStatement("SELECT id FROM interests WHERE code=?")) {
            int order = 1;
            for (QuestionSeed question : questions()) {
                insertQ.setString(1, question.key()); insertQ.setString(2, question.prompt()); insertQ.setInt(3, order++); insertQ.executeUpdate();
                long questionId = findId(selectQ, question.key());
                for (OptionSeed option : question.options()) {
                    insertOption.setLong(1, questionId); insertOption.setString(2, option.key()); insertOption.setString(3, option.label()); insertOption.executeUpdate();
                    selectOption.setLong(1, questionId); selectOption.setString(2, option.key());
                    long optionId;
                    try (ResultSet rs = selectOption.executeQuery()) { rs.next(); optionId = rs.getLong(1); }
                    for (Map.Entry<String, Integer> mapping : option.weights().entrySet()) {
                        insertMap.setLong(1, optionId); insertMap.setLong(2, findId(selectInterest, mapping.getKey()));
                        insertMap.setInt(3, mapping.getValue()); insertMap.addBatch();
                    }
                }
            }
            insertMap.executeBatch();
        }
    }

    private void seedAccounts(Connection c) throws SQLException {
        List<AccountSeed> accounts = new ArrayList<>(List.of(
                new AccountSeed("kaminari.president", "Aiko Tanaka", "3rd Year", "Kaminari", Role.PRESIDENT, false),
                new AccountSeed("kaminari.vp", "Ren Sato", "2nd Year", "Kaminari", Role.VICE_PRESIDENT, false),
                new AccountSeed("peer.president", "Maya Rivera", "4th Year", "Peer", Role.PRESIDENT, false),
                new AccountSeed("peer.vp", "Theo Brooks", "3rd Year", "Peer", Role.VICE_PRESIDENT, false),
                new AccountSeed("ichiban.president", "Yuna Mori", "3rd Year", "Ichiban", Role.PRESIDENT, false),
                new AccountSeed("ichiban.vp", "Kai Flores", "2nd Year", "Ichiban", Role.VICE_PRESIDENT, false),
                new AccountSeed("tsuushin.president", "Hana Cruz", "4th Year", "Tsuushin", Role.PRESIDENT, false),
                new AccountSeed("tsuushin.vp", "Leo Santos", "2nd Year", "Tsuushin", Role.VICE_PRESIDENT, false),
                new AccountSeed("kendo.president", "Daichi Reyes", "4th Year", "Kendo", Role.PRESIDENT, false),
                new AccountSeed("kendo.vp", "Mika Lopez", "3rd Year", "Kendo", Role.VICE_PRESIDENT, false),
                new AccountSeed("redcross.president", "Nina Garcia", "3rd Year", "Red Cross", Role.PRESIDENT, false),
                new AccountSeed("redcross.vp", "Owen Kim", "2nd Year", "Red Cross", Role.VICE_PRESIDENT, false),
                new AccountSeed("student.demo", "Jamie Park", "1st Year", null, Role.MEMBER, true),
                new AccountSeed("student.mina", "Mina Chen", "2nd Year", "Peer", Role.MEMBER, true),
                new AccountSeed("student.kai", "Sam Taylor", "1st Year", "Ichiban", Role.MEMBER, true)
        ));
        accounts.addAll(additionalMembers());
        char[] password = DEMO_PASSWORD.toCharArray();
        try (PreparedStatement exists = c.prepareStatement("SELECT 1 FROM users WHERE username=?");
             PreparedStatement insert = c.prepareStatement("INSERT INTO users(username,password_hash,full_name,academic_year,created_at_ms) VALUES(?,?,?,?,?)")) {
            for (AccountSeed account : accounts) {
                exists.setString(1, account.username());
                try (ResultSet rs = exists.executeQuery()) { if (rs.next()) continue; }
                char[] copy = password.clone();
                String encoded;
                try { encoded = hasher.hash(copy); } finally { Arrays.fill(copy, '\0'); }
                insert.setString(1, account.username()); insert.setString(2, encoded); insert.setString(3, account.name());
                insert.setString(4, account.year()); insert.setLong(5, Instant.now().toEpochMilli()); insert.executeUpdate();
            }
        } finally { Arrays.fill(password, '\0'); }
    }

    private void seedMemberships(Connection c) throws SQLException {
        List<AccountSeed> accounts = new ArrayList<>(List.of(
                new AccountSeed("kaminari.president", "", "", "Kaminari", Role.PRESIDENT, false), new AccountSeed("kaminari.vp", "", "", "Kaminari", Role.VICE_PRESIDENT, false),
                new AccountSeed("peer.president", "", "", "Peer", Role.PRESIDENT, false), new AccountSeed("peer.vp", "", "", "Peer", Role.VICE_PRESIDENT, false),
                new AccountSeed("ichiban.president", "", "", "Ichiban", Role.PRESIDENT, false), new AccountSeed("ichiban.vp", "", "", "Ichiban", Role.VICE_PRESIDENT, false),
                new AccountSeed("tsuushin.president", "", "", "Tsuushin", Role.PRESIDENT, false), new AccountSeed("tsuushin.vp", "", "", "Tsuushin", Role.VICE_PRESIDENT, false),
                new AccountSeed("kendo.president", "", "", "Kendo", Role.PRESIDENT, false), new AccountSeed("kendo.vp", "", "", "Kendo", Role.VICE_PRESIDENT, false),
                new AccountSeed("redcross.president", "", "", "Red Cross", Role.PRESIDENT, false), new AccountSeed("redcross.vp", "", "", "Red Cross", Role.VICE_PRESIDENT, false),
                new AccountSeed("student.mina", "", "", "Peer", Role.MEMBER, true), new AccountSeed("student.kai", "", "", "Ichiban", Role.MEMBER, true)
        ));
        accounts.addAll(additionalMembers());
        long now = Instant.now().toEpochMilli();
        try (PreparedStatement user = c.prepareStatement("SELECT id FROM users WHERE username=?");
             PreparedStatement club = c.prepareStatement("SELECT id FROM clubs WHERE name=?");
             PreparedStatement insert = c.prepareStatement("INSERT INTO club_memberships(user_id,club_id,role,status,joined_at_ms,updated_at_ms) VALUES(?,?,?,'ACTIVE',?,?) ON CONFLICT(user_id,club_id) DO NOTHING")) {
            for (AccountSeed account : accounts) {
                long userId = findId(user, account.username()); long clubId = findId(club, account.club());
                insert.setLong(1, userId); insert.setLong(2, clubId); insert.setString(3, account.role().name());
                insert.setLong(4, now); insert.setLong(5, now); insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private static List<AccountSeed> additionalMembers() {
        return List.of(
                new AccountSeed("kaminari.member.1", "Emi Watanabe", "2nd Year", "Kaminari", Role.MEMBER, true),
                new AccountSeed("kaminari.member.2", "Haru Nakamura", "1st Year", "Kaminari", Role.MEMBER, true),
                new AccountSeed("kaminari.member.3", "Sofia Garcia", "3rd Year", "Kaminari", Role.MEMBER, true),
                new AccountSeed("peer.member.1", "Luca Patel", "2nd Year", "Peer", Role.MEMBER, true),
                new AccountSeed("peer.member.2", "Avery Johnson", "1st Year", "Peer", Role.MEMBER, true),
                new AccountSeed("peer.member.3", "Noor Ali", "3rd Year", "Peer", Role.MEMBER, true),
                new AccountSeed("ichiban.member.1", "Hana Ito", "2nd Year", "Ichiban", Role.MEMBER, true),
                new AccountSeed("ichiban.member.2", "Miguel Reyes", "1st Year", "Ichiban", Role.MEMBER, true),
                new AccountSeed("ichiban.member.3", "Chloe Martin", "3rd Year", "Ichiban", Role.MEMBER, true),
                new AccountSeed("tsuushin.member.1", "Yuki Sato", "2nd Year", "Tsuushin", Role.MEMBER, true),
                new AccountSeed("tsuushin.member.2", "Olivia Chen", "1st Year", "Tsuushin", Role.MEMBER, true),
                new AccountSeed("tsuushin.member.3", "Mateo Cruz", "3rd Year", "Tsuushin", Role.MEMBER, true),
                new AccountSeed("kendo.member.1", "Kenji Tanaka", "2nd Year", "Kendo", Role.MEMBER, true),
                new AccountSeed("kendo.member.2", "Isabel Ramos", "1st Year", "Kendo", Role.MEMBER, true),
                new AccountSeed("kendo.member.3", "Noah Kim", "3rd Year", "Kendo", Role.MEMBER, true),
                new AccountSeed("redcross.member.1", "Amina Hassan", "2nd Year", "Red Cross", Role.MEMBER, true),
                new AccountSeed("redcross.member.2", "Ethan Flores", "1st Year", "Red Cross", Role.MEMBER, true),
                new AccountSeed("redcross.member.3", "Priya Shah", "3rd Year", "Red Cross", Role.MEMBER, true)
        );
    }

    private void seedSamples(Connection c) throws SQLException {
        long now = Instant.now().toEpochMilli();
        long future = now + 3L * 24 * 60 * 60 * 1000;
        try (PreparedStatement insert = c.prepareStatement("INSERT INTO club_events(club_id,created_by,title,description,location,starts_at_ms,ends_at_ms,created_at_ms,updated_at_ms) SELECT ?,?,? ,?,?,?, ?,?,? WHERE NOT EXISTS (SELECT 1 FROM club_events WHERE club_id=? AND title=?)")) {
            addEventSeed(c, insert, "Kaminari", "kaminari.president", "Kaminari open studio", "Meet members and try a short music and dance workshop.", "Arts Building, Room 104", future);
            addEventSeed(c, insert, "Peer", "peer.president", "Peer welcome circle", "A relaxed introduction to the Peer community.", "Student Center, Room 12", future + 86_400_000L);
            addEventSeed(c, insert, "Red Cross", "redcross.president", "First aid basics", "Learn practical first aid fundamentals with the club.", "Health Sciences Hall", future + 2L * 86_400_000L);
            insert.executeBatch();
        }
        long tsuushinId = findId(c, "SELECT id FROM clubs WHERE name=?", "Tsuushin");
        long minaId = findId(c, "SELECT id FROM users WHERE username=?", "student.mina");
        long appId = insertSeedApplication(c, minaId, tsuushinId, "I enjoy writing about student life and would like to contribute to campus reporting.", "PENDING", now, null, null, null);
        addHistory(c, appId, null, "PENDING", minaId, "Sample application for local demonstration", now);

        long kaminariId = findId(c, "SELECT id FROM clubs WHERE name=?", "Kaminari");
        long kaiId = findId(c, "SELECT id FROM users WHERE username=?", "student.kai");
        long kaminariAdmin = findId(c, "SELECT u.id FROM users u JOIN club_memberships m ON m.user_id=u.id JOIN clubs c ON c.id=m.club_id WHERE u.username=? AND c.name=?", "kaminari.president", "Kaminari");
        long scheduledAt = now + 7L * 86_400_000L;
        long scheduledAppId = insertSeedApplication(c, kaiId, kaminariId, "I want to learn performance skills and collaborate with other students.", "PENDING", now, null, null, null);
        addHistory(c, scheduledAppId, null, "PENDING", kaiId, "Sample application for local demonstration", now);
        scheduleSeedApplication(c, scheduledAppId, scheduledAt, "Arts Building lobby", kaminariAdmin, now + 1);
        addHistory(c, scheduledAppId, "PENDING", "INTERVIEW_SCHEDULED", kaminariAdmin, "Sample interview proposal", now + 1);
    }

    private static void addEventSeed(Connection c, PreparedStatement ps, String clubName, String creator, String title,
                                     String description, String location, long start) throws SQLException {
        long clubId = findId(c, "SELECT id FROM clubs WHERE name=?", clubName);
        long creatorId = findId(c, "SELECT id FROM users WHERE username=?", creator);
        ps.setLong(1, clubId); ps.setLong(2, creatorId); ps.setString(3, title); ps.setString(4, description);
        ps.setString(5, location); ps.setLong(6, start); ps.setLong(7, start + 90L * 60_000L);
        ps.setLong(8, start); ps.setLong(9, start); ps.setLong(10, clubId); ps.setString(11, title); ps.addBatch();
    }

    private static long insertSeedApplication(Connection c, long applicant, long club, String motivation, String status,
                                              long submitted, Long interview, String location, Long scheduledBy) throws SQLException {
        try (PreparedStatement find = c.prepareStatement("SELECT id FROM club_applications WHERE applicant_id=? AND club_id=? AND motivation=?")) {
            find.setLong(1, applicant); find.setLong(2, club); find.setString(3, motivation);
            try (ResultSet rs = find.executeQuery()) { if (rs.next()) return rs.getLong(1); }
        }
        try (PreparedStatement insert = c.prepareStatement("INSERT INTO club_applications(applicant_id,club_id,motivation,status,submitted_at_ms,updated_at_ms,interview_at_ms,interview_location,interview_scheduled_by) VALUES(?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            insert.setLong(1, applicant); insert.setLong(2, club); insert.setString(3, motivation); insert.setString(4, status);
            insert.setLong(5, submitted); insert.setLong(6, submitted);
            if (interview == null) insert.setNull(7, java.sql.Types.INTEGER); else insert.setLong(7, interview);
            insert.setString(8, location);
            if (scheduledBy == null) insert.setNull(9, java.sql.Types.INTEGER); else insert.setLong(9, scheduledBy);
            insert.executeUpdate();
            try (ResultSet keys = insert.getGeneratedKeys()) { if (!keys.next()) throw new SQLException("Could not create sample application."); return keys.getLong(1); }
        }
    }

    private static void scheduleSeedApplication(Connection c, long applicationId, long interviewAt, String location,
                                                long scheduledBy, long updatedAt) throws SQLException {
        try (PreparedStatement update = c.prepareStatement("UPDATE club_applications SET status='INTERVIEW_SCHEDULED',interview_at_ms=?,interview_location=?,interview_scheduled_by=?,updated_at_ms=? WHERE id=? AND status='PENDING'")) {
            update.setLong(1, interviewAt); update.setString(2, location); update.setLong(3, scheduledBy);
            update.setLong(4, updatedAt); update.setLong(5, applicationId); update.executeUpdate();
        }
    }

    private static void addHistory(Connection c, long appId, String previous, String next, long actor,
                                   String note, long timestamp) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO application_status_history(application_id,previous_status,new_status,changed_by,note,changed_at_ms) SELECT ?,?,?,?,?,? WHERE NOT EXISTS (SELECT 1 FROM application_status_history WHERE application_id=? AND new_status=? AND note=?)")) {
            ps.setLong(1, appId); if (previous == null) ps.setNull(2, java.sql.Types.VARCHAR); else ps.setString(2, previous);
            ps.setString(3, next); ps.setLong(4, actor); ps.setString(5, note); ps.setLong(6, timestamp);
            ps.setLong(7, appId); ps.setString(8, next); ps.setString(9, note); ps.executeUpdate();
        }
    }

    private static long findId(PreparedStatement ps, String value) throws SQLException {
        ps.setString(1, value);
        try (ResultSet rs = ps.executeQuery()) { if (!rs.next()) throw new SQLException("Missing seeded reference value: " + value); return rs.getLong(1); }
    }

    private static long findId(PreparedStatement ps, String first, String second) throws SQLException {
        ps.setString(1, first); ps.setString(2, second);
        try (ResultSet rs = ps.executeQuery()) { if (!rs.next()) throw new SQLException("Missing seeded account membership."); return rs.getLong(1); }
    }

    private static long findId(Connection c, String sql, String... values) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) ps.setString(i + 1, values[i]);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("Missing seeded value: " + String.join(" / ", values));
                return rs.getLong(1);
            }
        }
    }
}
