package com.kizuna.service;

import com.kizuna.db.Database;
import com.kizuna.db.DemoSeeder;
import com.kizuna.model.Models.ApplicationStatus;
import com.kizuna.model.Models.EventForm;
import com.kizuna.model.Models.UserProfile;
import com.kizuna.repository.ApplicationRepository;
import com.kizuna.repository.AssessmentRepository;
import com.kizuna.repository.ClubRepository;
import com.kizuna.repository.EventRepository;
import com.kizuna.repository.MembershipRepository;
import com.kizuna.repository.UserRepository;
import com.kizuna.util.AvatarStore;
import com.kizuna.util.PasswordHasher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowIntegrationTest {
    @TempDir Path temp;

    @Test
    void authAssessmentApplicationsMembershipsAndEventsWorkAcrossTransactions() throws Exception {
        Database database = new Database(temp.resolve("workflow.db"));
        database.initialize();
        PasswordHasher hasher = new PasswordHasher();
        database.transaction(c -> { new DemoSeeder(hasher).seed(c); return null; });

        UserRepository users = new UserRepository(); ClubRepository clubs = new ClubRepository();
        MembershipRepository membershipRepo = new MembershipRepository(clubs);
        ApplicationRepository applicationRepo = new ApplicationRepository();
        Session session = new Session();
        AuthorizationService authorization = new AuthorizationService(session, membershipRepo);
        AuthService auth = new AuthService(database, users, hasher, new AvatarStore(temp.resolve("avatars")), session);
        AssessmentService assessment = new AssessmentService(database, session, new AssessmentRepository(), users, new RecommendationEngine());
        MembershipService memberships = new MembershipService(database, session, authorization, membershipRepo);
        ApplicationService applications = new ApplicationService(database, session, authorization, membershipRepo, applicationRepo);
        EventService events = new EventService(database, session, authorization, new EventRepository());

        char[] correct = DemoSeeder.DEMO_PASSWORD.toCharArray();
        UserProfile demo = auth.login(" STUDENT.DEMO ", correct);
        assertEquals("student.demo", demo.username());
        assertTrue(allZero(correct));
        auth.logout();
        String wrongPasswordMessage = assertThrows(IllegalArgumentException.class,
                () -> auth.login("student.demo", "incorrect password".toCharArray())).getMessage();
        auth.logout();
        String unknownUserMessage = assertThrows(IllegalArgumentException.class,
                () -> auth.login("nobody-here", "incorrect password".toCharArray())).getMessage();
        assertEquals(wrongPasswordMessage, unknownUserMessage);

        char[] signupPassword = "a new safe passphrase".toCharArray();
        UserProfile newStudent = auth.signup(" New.Student ", signupPassword, "New Student", "1st Year", null);
        assertEquals("new.student", newStudent.username());
        assertTrue(allZero(signupPassword));
        database.read(c -> {
            assertEquals(0, scalar(c, "SELECT count(*) FROM club_memberships WHERE user_id=?", newStudent.id()));
            assertTrue(users.findAccount(c, newStudent.username()).passwordHash().startsWith("pbkdf2_sha256$600000$"));
            return null;
        });
        session.signIn(demo);

        assertNull(demo.assessmentCompletedAt());
        var questions = assessment.questions();
        Map<Long, Long> firstAnswers = new HashMap<>();
        for (var question : questions) firstAnswers.put(question.id(), question.options().getFirst().id());
        var firstResult = assessment.submit(firstAnswers);
        assertEquals(3, firstResult.recommendations().size());
        database.read(c -> {
            assertEquals(questions.size(), scalar(c, "SELECT count(*) FROM assessment_answers WHERE user_id=?", demo.id()));
            assertEquals(6, scalar(c, "SELECT count(*) FROM assessment_scores WHERE user_id=?", demo.id()));
            assertTrue(scalar(c, "SELECT count(*) FROM user_interests WHERE user_id=?", demo.id()) > 0);
            return null;
        });
        Map<Long, Long> retakeAnswers = new HashMap<>();
        for (var question : questions) retakeAnswers.put(question.id(), question.options().getLast().id());
        assessment.submit(retakeAnswers);
        database.read(c -> {
            assertEquals(questions.size(), scalar(c, "SELECT count(*) FROM assessment_answers WHERE user_id=?", demo.id()));
            assertEquals(6, scalar(c, "SELECT count(*) FROM assessment_scores WHERE user_id=?", demo.id()));
            assertNotNull(users.findProfile(c, demo.id()).assessmentCompletedAt());
            return null;
        });

        long kaminariId = database.read(c -> clubs.findByName(c, "Kaminari").id());
        long redCrossId = database.read(c -> clubs.findByName(c, "Red Cross").id());
        long applicationId = applications.apply(kaminariId, "I would like to learn performance and meet other students.", "School performances", "Weekends");
        assertThrows(IllegalStateException.class, () -> applications.apply(kaminariId, "Second application", null, null));
        assertFalse(assessment.recommendations().stream().anyMatch(item -> item.clubId() == kaminariId));
        int persistedScoreCount = database.read(c -> scalar(c, "SELECT count(*) FROM assessment_scores WHERE user_id=?", demo.id()));
        assertEquals(6, persistedScoreCount);

        assertThrows(SecurityException.class, () -> applications.applicationsForClub(redCrossId));
        UserProfile kaminariAdmin = profile(database, users, "kaminari.president");
        session.signIn(kaminariAdmin);
        assertThrows(SecurityException.class, () -> applications.applicationsForClub(redCrossId));
        long proposed = Instant.now().plusSeconds(86_400).toEpochMilli();
        applications.scheduleInterview(applicationId, proposed, "Arts Building, Room 104");
        applications.scheduleInterview(applicationId, proposed + 86_400_000, "Arts Building lobby");
        assertThrows(IllegalArgumentException.class, () -> applications.scheduleInterview(applicationId, 1, "Past date"));
        session.signIn(demo);
        applications.confirmInterview(applicationId);
        assertThrows(IllegalStateException.class, () -> applications.confirmInterview(applicationId));
        session.signIn(kaminariAdmin);
        applications.accept(applicationId);
        database.read(c -> {
            assertEquals(1, scalar(c, "SELECT count(*) FROM club_memberships WHERE user_id=? AND club_id=? AND role='MEMBER' AND status='ACTIVE'", demo.id(), kaminariId));
            assertEquals(1, scalar(c, "SELECT count(*) FROM club_applications WHERE id=? AND status='ACCEPTED'", applicationId));
            assertEquals(5, scalar(c, "SELECT count(*) FROM application_status_history WHERE application_id=?", applicationId));
            return null;
        });
        session.signIn(profile(database, users, "student.mina"));
        assertThrows(SecurityException.class, () -> applications.confirmInterview(applicationId));
        session.signIn(kaminariAdmin);
        long sampleScheduledId = database.read(c -> scalar(c, "SELECT a.id FROM club_applications a JOIN users u ON u.id=a.applicant_id WHERE u.username='student.kai' AND a.club_id=?", kaminariId));
        applications.reject(sampleScheduledId);
        database.read(c -> {
            assertEquals(1, scalar(c, "SELECT count(*) FROM club_applications WHERE id=? AND status='REJECTED'", sampleScheduledId));
            assertEquals(0, scalar(c, "SELECT count(*) FROM club_memberships WHERE user_id=(SELECT id FROM users WHERE username='student.kai') AND club_id=? AND status='ACTIVE'", kaminariId));
            return null;
        });

        UserProfile mina = profile(database, users, "student.mina");
        session.signIn(mina);
        long pendingForFailure = applications.apply(kaminariId, "I would like to join the creative team.", null, null);
        session.signIn(kaminariAdmin);
        applications.scheduleInterview(pendingForFailure, proposed, "Arts Building lobby");
        session.signIn(mina);
        applications.confirmInterview(pendingForFailure);
        session.signIn(kaminariAdmin);
        ApplicationService failingAccept = new ApplicationService(database, session, authorization, membershipRepo, applicationRepo,
                () -> { throw new IllegalStateException("injected failure"); });
        assertThrows(IllegalStateException.class, () -> failingAccept.accept(pendingForFailure));
        database.read(c -> {
            assertEquals(1, scalar(c, "SELECT count(*) FROM club_applications WHERE id=? AND status='INTERVIEW_CONFIRMED'", pendingForFailure));
            assertEquals(0, scalar(c, "SELECT count(*) FROM club_memberships WHERE user_id=? AND club_id=? AND status='ACTIVE'", mina.id(), kaminariId));
            assertEquals(3, scalar(c, "SELECT count(*) FROM application_status_history WHERE application_id=?", pendingForFailure));
            return null;
        });

        long redCrossApplication = applications.apply(redCrossId, "I want to help the community prepare for emergencies.", null, null);
        long redCrossAdminId = database.read(c -> scalar(c, "SELECT id FROM users WHERE username='redcross.president'"));
        assertThrows(java.sql.SQLException.class, () -> database.transaction(c -> {
            try (var ps = c.prepareStatement("UPDATE club_applications SET status='ACCEPTED',reviewed_by=?,reviewed_at_ms=?,interview_at_ms=?,interview_location='Room 1',interview_scheduled_by=? WHERE id=?")) {
                long now = Instant.now().toEpochMilli(); ps.setLong(1, redCrossAdminId); ps.setLong(2, now); ps.setLong(3, now + 100_000); ps.setLong(4, redCrossAdminId); ps.setLong(5, redCrossApplication); ps.executeUpdate();
            }
            return null;
        }));
        assertThrows(SecurityException.class, () -> applications.scheduleInterview(redCrossApplication, proposed, "Wrong club"));
        assertThrows(SecurityException.class, () -> events.create(new EventForm(redCrossId, "Wrong club", "A test", "Room", proposed, proposed + 3600_000)));

        EventForm event = new EventForm(kaminariId, "Spring open studio", "Try a short workshop.", "Arts Building", proposed + 200_000, proposed + 3_800_000);
        long eventId = events.create(event);
        assertTrue(events.upcoming().stream().anyMatch(item -> item.id() == eventId));
        events.edit(eventId, new EventForm(kaminariId, "Spring studio", "Updated event details.", "Arts Building, Room 105", proposed + 400_000, proposed + 4_000_000));
        assertTrue(events.forClub(kaminariId).stream().anyMatch(item -> item.id() == eventId && item.title().equals("Spring studio")));
        assertThrows(IllegalArgumentException.class, () -> events.create(new EventForm(kaminariId, "Bad", "Bad time", "Room", proposed, proposed)));
        events.deactivate(eventId);
        assertFalse(events.upcoming().stream().anyMatch(item -> item.id() == eventId));

        long peerId = database.read(c -> clubs.findByName(c, "Peer").id());
        long kendoId = database.read(c -> clubs.findByName(c, "Kendo").id());
        database.transaction(c -> {
            try (var ps = c.prepareStatement("INSERT INTO club_memberships(user_id,club_id,role,status,joined_at_ms,updated_at_ms) VALUES(?,?,'MEMBER','ACTIVE',?,?) ON CONFLICT(user_id,club_id) DO NOTHING")) {
                long now = Instant.now().toEpochMilli(); ps.setLong(1, mina.id()); ps.setLong(2, kendoId); ps.setLong(3, now); ps.setLong(4, now); ps.executeUpdate();
            }
            return null;
        });
        session.signIn(profile(database, users, "peer.president"));
        assertEquals(1, memberships.administeredClubs().size());
        memberships.removeMember(peerId, mina.id());
        assertThrows(IllegalArgumentException.class, () -> memberships.removeMember(peerId, kaminariAdmin.id()));
        database.read(c -> {
            assertEquals(1, scalar(c, "SELECT count(*) FROM club_memberships WHERE user_id=? AND club_id=? AND status='INACTIVE'", mina.id(), peerId));
            assertEquals(1, scalar(c, "SELECT count(*) FROM club_memberships WHERE user_id=? AND club_id=? AND status='ACTIVE'", mina.id(), kendoId));
            assertEquals(1, scalar(c, "SELECT count(*) FROM users WHERE id=?", mina.id()));
            return null;
        });
        var network = memberships.network();
        assertFalse(network.stream().filter(item -> item.club().id() == peerId).findFirst().orElseThrow().members().stream().anyMatch(member -> member.userId() == mina.id()));
        assertTrue(network.stream().filter(item -> item.club().id() == kendoId).findFirst().orElseThrow().members().stream().anyMatch(member -> member.userId() == mina.id()));
    }

    private static UserProfile profile(Database database, UserRepository users, String username) throws Exception {
        return database.read(c -> users.findAccount(c, username).profile());
    }

    private static int scalar(java.sql.Connection c, String sql, Object... args) throws Exception {
        try (var ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            try (var rs = ps.executeQuery()) { rs.next(); return rs.getInt(1); }
        }
    }
    private static boolean allZero(char[] chars) { for (char c : chars) if (c != '\0') return false; return true; }
}
