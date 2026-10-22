package com.kizuna;

import com.kizuna.db.Database;
import com.kizuna.db.DemoSeeder;
import com.kizuna.db.Schema;
import com.kizuna.util.PasswordHasher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseBootstrapTest {
    @TempDir Path temp;

    @Test
    void createsAndSeedsSchemaIdempotentlyWithForeignKeysOnEveryConnection() throws Exception {
        Database db = new Database(temp.resolve("fresh.db"));
        db.initialize();
        DemoSeeder seeder = new DemoSeeder(new PasswordHasher());
        db.transaction(c -> { seeder.seed(c); return null; });
        db.transaction(c -> { seeder.seed(c); return null; });

        db.read(c -> {
            assertEquals(1, pragma(c, "PRAGMA foreign_keys"));
            assertEquals(5000, pragma(c, "PRAGMA busy_timeout"));
            assertEquals(Schema.VERSION, pragma(c, "PRAGMA user_version"));
            assertEquals(6, count(c, "clubs"));
            assertEquals(33, count(c, "users"));
            assertEquals(12, scalar(c, "SELECT count(*) FROM club_memberships WHERE role IN ('PRESIDENT','VICE_PRESIDENT') AND status='ACTIVE'"));
            assertEquals(18, scalar(c, "SELECT count(*) FROM club_memberships m JOIN users u ON u.id=m.user_id WHERE u.username LIKE '%.member.%' AND m.role='MEMBER' AND m.status='ACTIVE'"));
            assertEquals(6, scalar(c, "SELECT count(*) FROM (SELECT m.club_id FROM club_memberships m JOIN users u ON u.id=m.user_id WHERE u.username LIKE '%.member.%' AND m.role='MEMBER' AND m.status='ACTIVE' GROUP BY m.club_id HAVING count(*)=3)"));
            assertEquals(4, count(c, "assessment_questions"));
            assertEquals(3, count(c, "club_events"));
            assertEquals(2, count(c, "club_applications"));
            assertEquals(0, count(c, "assessment_scores"));
            assertEquals(2, scalar(c, "SELECT count(*) FROM sqlite_master WHERE type='trigger' AND name IN ('trg_valid_application_status_transition','trg_application_must_start_pending')"));
            assertEquals(33, scalar(c, "SELECT count(*) FROM users WHERE password_hash LIKE 'pbkdf2_sha256$600000$%'"));
            assertEquals(0, scalar(c, "SELECT count(*) FROM users WHERE password_hash=?", DemoSeeder.DEMO_PASSWORD));
            assertIntegrityGuards(c);
            return null;
        });
    }

    @Test
    void refusesUnknownNewerSchemaVersion() throws Exception {
        Database db = new Database(temp.resolve("newer.db"));
        try (var c = db.open(); var s = c.createStatement()) { s.execute("PRAGMA user_version=3"); }
        SQLException exception = assertThrows(SQLException.class, db::initialize);
        assertTrue(exception.getMessage().contains("newer"));
    }

    @Test
    void initializesInPathWithSpacesAndRefusesUnversionedExistingData() throws Exception {
        Path freshFile = temp.resolve("Kizuna User Data").resolve("local database.db");
        Database fresh = new Database(freshFile);
        fresh.initialize();
        assertTrue(Files.isRegularFile(freshFile));
        int freshVersion = fresh.read(c -> pragma(c, "PRAGMA user_version"));
        assertEquals(Schema.VERSION, freshVersion);

        Path existingFile = temp.resolve("existing.db");
        Database existing = new Database(existingFile);
        try (var connection = existing.open(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE saved_data (id INTEGER PRIMARY KEY, value TEXT NOT NULL)");
            statement.execute("INSERT INTO saved_data VALUES (1, 'keep this data')");
        }

        SQLException exception = assertThrows(SQLException.class, existing::initialize);
        assertTrue(exception.getMessage().contains("no schema version"));
        existing.read(c -> {
            assertEquals(0, pragma(c, "PRAGMA user_version"));
            assertEquals("keep this data", scalarString(c, "SELECT value FROM saved_data WHERE id=1"));
            return null;
        });
    }

    @Test
    void migratesVersionOneByApplyingCurrentIdempotentSchema() throws Exception {
        Database db = new Database(temp.resolve("version-one.db"));
        db.initialize();
        try (var c = db.open(); var s = c.createStatement()) {
            s.execute("DROP TRIGGER trg_application_must_start_pending");
            s.execute("PRAGMA user_version=1");
        }

        db.initialize();
        db.read(c -> {
            assertEquals(Schema.VERSION, pragma(c, "PRAGMA user_version"));
            assertEquals(1, scalar(c, "SELECT count(*) FROM sqlite_master WHERE type='trigger' AND name='trg_application_must_start_pending'"));
            return null;
        });
    }

    private static int pragma(java.sql.Connection c, String sql) throws SQLException {
        try (var s = c.createStatement(); var rs = s.executeQuery(sql)) { rs.next(); return rs.getInt(1); }
    }
    private static int count(java.sql.Connection c, String table) throws SQLException { return scalar(c, "SELECT count(*) FROM " + table); }
    private static int scalar(java.sql.Connection c, String sql, Object... args) throws SQLException {
        try (var ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            try (var rs = ps.executeQuery()) { rs.next(); return rs.getInt(1); }
        }
    }

    private static String scalarString(java.sql.Connection c, String sql) throws SQLException {
        try (var statement = c.createStatement(); var result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getString(1);
        }
    }

    private static void assertIntegrityGuards(java.sql.Connection c) throws Exception {
        assertSqlFails(c, "INSERT INTO users(username,password_hash,full_name,academic_year,created_at_ms) VALUES('STUDENT.DEMO','a sufficiently long fake hash','Duplicate','1st Year',1)");
        assertSqlFails(c, "INSERT INTO clubs(name,description) VALUES('kaminari','duplicate')");
        assertSqlFails(c, "INSERT INTO interests(code,name) VALUES('music','Different name')");
        assertSqlFails(c, "INSERT INTO club_memberships(user_id,club_id,role,status,joined_at_ms,updated_at_ms) VALUES((SELECT id FROM users WHERE username='student.mina'),(SELECT id FROM clubs WHERE name='Peer'),'MEMBER','ACTIVE',1,1)");
        assertSqlFails(c, "INSERT INTO club_memberships(user_id,club_id,role,status,joined_at_ms,updated_at_ms) VALUES((SELECT id FROM users WHERE username='student.demo'),(SELECT id FROM clubs WHERE name='Kendo'),'NOT_A_ROLE','ACTIVE',1,1)");
        assertSqlFails(c, "INSERT INTO club_memberships(user_id,club_id,role,status,joined_at_ms,updated_at_ms) VALUES((SELECT id FROM users WHERE username='student.demo'),(SELECT id FROM clubs WHERE name='Kaminari'),'PRESIDENT','ACTIVE',1,1)");
        assertSqlFails(c, "INSERT INTO club_memberships(user_id,club_id,role,status,joined_at_ms,updated_at_ms) VALUES((SELECT id FROM users WHERE username='student.demo'),(SELECT id FROM clubs WHERE name='Peer'),'VICE_PRESIDENT','ACTIVE',1,1)");
        assertSqlFails(c, "INSERT INTO user_interests(user_id,interest_id,score,updated_at_ms) VALUES(999999,1,1,1)");
        assertSqlFails(c, "INSERT INTO assessment_option_interests(option_id,interest_id,weight) VALUES((SELECT o.id FROM assessment_options o JOIN assessment_questions q ON q.id=o.question_id WHERE q.question_key='campus_activity' AND o.option_key='arts'),(SELECT id FROM interests WHERE code='MUSIC'),0)");
        assertSqlFails(c, "INSERT INTO club_applications(applicant_id,club_id,motivation,status,submitted_at_ms,updated_at_ms) VALUES((SELECT id FROM users WHERE username='student.mina'),(SELECT id FROM clubs WHERE name='Tsuushin'),'Duplicate active application','PENDING',1,1)");
        assertSqlFails(c, "INSERT INTO club_applications(applicant_id,club_id,motivation,status,submitted_at_ms,updated_at_ms,reviewed_by,reviewed_at_ms) VALUES((SELECT id FROM users WHERE username='student.demo'),(SELECT id FROM clubs WHERE name='Red Cross'),'Terminal state cannot be created','REJECTED',1,1,(SELECT id FROM users WHERE username='redcross.president'),1)");
        assertSqlFails(c, "INSERT INTO club_events(club_id,created_by,title,description,location,starts_at_ms,ends_at_ms,created_at_ms,updated_at_ms) VALUES((SELECT id FROM clubs WHERE name='Kendo'),(SELECT id FROM users WHERE username='kaminari.president'),'Wrong creator','Description','Room',2000,3000,1,1)");
        assertSqlFails(c, "INSERT INTO club_events(club_id,created_by,title,description,location,starts_at_ms,ends_at_ms,created_at_ms,updated_at_ms) VALUES((SELECT id FROM clubs WHERE name='Kendo'),(SELECT id FROM users WHERE username='kendo.president'),'Bad time','Description','Room',3000,2000,1,1)");
        assertSqlFails(c, "INSERT INTO club_events(club_id,created_by,title,description,location,starts_at_ms,ends_at_ms,created_at_ms,updated_at_ms,is_active) VALUES((SELECT id FROM clubs WHERE name='Kendo'),(SELECT id FROM users WHERE username='kendo.president'),'Bad flag','Description','Room',2000,3000,1,1,2)");
        assertSqlFails(c, "UPDATE club_applications SET status='ACCEPTED',reviewed_by=(SELECT id FROM users WHERE username='tsuushin.president'),reviewed_at_ms=1 WHERE applicant_id=(SELECT id FROM users WHERE username='student.mina') AND club_id=(SELECT id FROM clubs WHERE name='Tsuushin')");
    }

    private static void assertSqlFails(java.sql.Connection c, String sql) throws Exception {
        try (var statement = c.createStatement()) { assertThrows(SQLException.class, () -> statement.executeUpdate(sql), sql); }
    }
}
