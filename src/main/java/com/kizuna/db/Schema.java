package com.kizuna.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

public final class Schema {
    public static final int VERSION = 2;

    private static final List<String> DDL = List.of(
            """
            CREATE TABLE IF NOT EXISTS users (
                id INTEGER PRIMARY KEY,
                username TEXT NOT NULL COLLATE NOCASE UNIQUE
                    CHECK (username = trim(username) AND length(trim(username)) BETWEEN 3 AND 32),
                password_hash TEXT NOT NULL CHECK (length(password_hash) > 20),
                full_name TEXT NOT NULL CHECK (length(trim(full_name)) BETWEEN 1 AND 80),
                academic_year TEXT NOT NULL CHECK (academic_year IN ('1st Year','2nd Year','3rd Year','4th Year','5th Year','Other')),
                photo_filename TEXT,
                created_at_ms INTEGER NOT NULL,
                assessment_completed_at_ms INTEGER,
                is_active INTEGER NOT NULL DEFAULT 1 CHECK (is_active IN (0,1))
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS clubs (
                id INTEGER PRIMARY KEY,
                name TEXT NOT NULL COLLATE NOCASE UNIQUE CHECK (length(trim(name)) BETWEEN 1 AND 80),
                description TEXT NOT NULL CHECK (length(trim(description)) BETWEEN 1 AND 1000)
            )
            """,
            "CREATE TABLE IF NOT EXISTS interests (id INTEGER PRIMARY KEY, code TEXT NOT NULL COLLATE NOCASE UNIQUE, name TEXT NOT NULL COLLATE NOCASE UNIQUE)",
            """
            CREATE TABLE IF NOT EXISTS club_memberships (
                id INTEGER PRIMARY KEY,
                user_id INTEGER NOT NULL,
                club_id INTEGER NOT NULL,
                role TEXT NOT NULL CHECK (role IN ('MEMBER','PRESIDENT','VICE_PRESIDENT')),
                status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
                joined_at_ms INTEGER NOT NULL,
                updated_at_ms INTEGER NOT NULL,
                UNIQUE (user_id, club_id),
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
                FOREIGN KEY (club_id) REFERENCES clubs(id) ON DELETE RESTRICT
            )
            """,
            "CREATE UNIQUE INDEX IF NOT EXISTS ux_one_active_president_per_club ON club_memberships(club_id) WHERE role='PRESIDENT' AND status='ACTIVE'",
            "CREATE UNIQUE INDEX IF NOT EXISTS ux_one_active_vice_president_per_club ON club_memberships(club_id) WHERE role='VICE_PRESIDENT' AND status='ACTIVE'",
            "CREATE INDEX IF NOT EXISTS ix_memberships_club_status ON club_memberships(club_id,status,role)",
            "CREATE INDEX IF NOT EXISTS ix_memberships_user_status_role ON club_memberships(user_id,status,role)",
            """
            CREATE TABLE IF NOT EXISTS user_interests (
                user_id INTEGER NOT NULL,
                interest_id INTEGER NOT NULL,
                score INTEGER NOT NULL CHECK (score > 0),
                updated_at_ms INTEGER NOT NULL,
                PRIMARY KEY (user_id,interest_id),
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                FOREIGN KEY (interest_id) REFERENCES interests(id) ON DELETE RESTRICT
            )
            """,
            "CREATE INDEX IF NOT EXISTS ix_user_interests_interest ON user_interests(interest_id,user_id)",
            """
            CREATE TABLE IF NOT EXISTS club_interests (
                club_id INTEGER NOT NULL,
                interest_id INTEGER NOT NULL,
                PRIMARY KEY (club_id,interest_id),
                FOREIGN KEY (club_id) REFERENCES clubs(id) ON DELETE RESTRICT,
                FOREIGN KEY (interest_id) REFERENCES interests(id) ON DELETE RESTRICT
            )
            """,
            "CREATE INDEX IF NOT EXISTS ix_club_interests_interest ON club_interests(interest_id,club_id)",
            """
            CREATE TABLE IF NOT EXISTS assessment_questions (
                id INTEGER PRIMARY KEY,
                question_key TEXT NOT NULL COLLATE NOCASE UNIQUE,
                prompt TEXT NOT NULL CHECK (length(trim(prompt)) BETWEEN 1 AND 500),
                sort_order INTEGER NOT NULL UNIQUE CHECK (sort_order > 0),
                is_active INTEGER NOT NULL DEFAULT 1 CHECK (is_active IN (0,1))
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS assessment_options (
                id INTEGER PRIMARY KEY,
                question_id INTEGER NOT NULL,
                option_key TEXT NOT NULL COLLATE NOCASE,
                label TEXT NOT NULL CHECK (length(trim(label)) BETWEEN 1 AND 300),
                UNIQUE (question_id,option_key),
                UNIQUE (id,question_id),
                FOREIGN KEY (question_id) REFERENCES assessment_questions(id) ON DELETE RESTRICT
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS assessment_option_interests (
                option_id INTEGER NOT NULL,
                interest_id INTEGER NOT NULL,
                weight INTEGER NOT NULL DEFAULT 1 CHECK (weight BETWEEN 1 AND 3),
                PRIMARY KEY (option_id,interest_id),
                FOREIGN KEY (option_id) REFERENCES assessment_options(id) ON DELETE RESTRICT,
                FOREIGN KEY (interest_id) REFERENCES interests(id) ON DELETE RESTRICT
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS assessment_answers (
                user_id INTEGER NOT NULL,
                question_id INTEGER NOT NULL,
                option_id INTEGER NOT NULL,
                answered_at_ms INTEGER NOT NULL,
                PRIMARY KEY (user_id,question_id),
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                FOREIGN KEY (question_id) REFERENCES assessment_questions(id) ON DELETE RESTRICT,
                FOREIGN KEY (option_id,question_id) REFERENCES assessment_options(id,question_id) ON DELETE RESTRICT
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS assessment_scores (
                user_id INTEGER NOT NULL,
                club_id INTEGER NOT NULL,
                score INTEGER NOT NULL CHECK (score >= 0),
                calculated_at_ms INTEGER NOT NULL,
                PRIMARY KEY (user_id,club_id),
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                FOREIGN KEY (club_id) REFERENCES clubs(id) ON DELETE RESTRICT
            )
            """,
            "CREATE INDEX IF NOT EXISTS ix_assessment_scores_user_score ON assessment_scores(user_id,score DESC,club_id)",
            """
            CREATE TABLE IF NOT EXISTS club_applications (
                id INTEGER PRIMARY KEY,
                applicant_id INTEGER NOT NULL,
                club_id INTEGER NOT NULL,
                motivation TEXT NOT NULL CHECK (length(trim(motivation)) BETWEEN 1 AND 500),
                experience TEXT CHECK (experience IS NULL OR length(experience) <= 300),
                availability_notes TEXT CHECK (availability_notes IS NULL OR length(availability_notes) <= 300),
                status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','INTERVIEW_SCHEDULED','INTERVIEW_CONFIRMED','ACCEPTED','REJECTED')),
                submitted_at_ms INTEGER NOT NULL,
                updated_at_ms INTEGER NOT NULL,
                interview_at_ms INTEGER,
                interview_location TEXT CHECK (interview_location IS NULL OR length(trim(interview_location)) <= 200),
                interview_scheduled_by INTEGER,
                interview_confirmed_at_ms INTEGER,
                reviewed_by INTEGER,
                reviewed_at_ms INTEGER,
                FOREIGN KEY (applicant_id) REFERENCES users(id) ON DELETE RESTRICT,
                FOREIGN KEY (club_id) REFERENCES clubs(id) ON DELETE RESTRICT,
                FOREIGN KEY (interview_scheduled_by,club_id) REFERENCES club_memberships(user_id,club_id) ON DELETE RESTRICT,
                FOREIGN KEY (reviewed_by,club_id) REFERENCES club_memberships(user_id,club_id) ON DELETE RESTRICT,
                CHECK (status NOT IN ('INTERVIEW_SCHEDULED','INTERVIEW_CONFIRMED','ACCEPTED') OR (interview_at_ms IS NOT NULL AND interview_location IS NOT NULL AND length(trim(interview_location))>0 AND interview_scheduled_by IS NOT NULL)),
                CHECK (status NOT IN ('INTERVIEW_CONFIRMED','ACCEPTED') OR interview_confirmed_at_ms IS NOT NULL),
                CHECK (status NOT IN ('ACCEPTED','REJECTED') OR (reviewed_by IS NOT NULL AND reviewed_at_ms IS NOT NULL))
            )
            """,
            "CREATE UNIQUE INDEX IF NOT EXISTS ux_one_active_application_per_user_club ON club_applications(applicant_id,club_id) WHERE status IN ('PENDING','INTERVIEW_SCHEDULED','INTERVIEW_CONFIRMED')",
            "CREATE INDEX IF NOT EXISTS ix_applications_club_status_submitted ON club_applications(club_id,status,submitted_at_ms)",
            "CREATE INDEX IF NOT EXISTS ix_applications_applicant_status ON club_applications(applicant_id,status)",
            """
            CREATE TABLE IF NOT EXISTS application_status_history (
                id INTEGER PRIMARY KEY,
                application_id INTEGER NOT NULL,
                previous_status TEXT CHECK (previous_status IS NULL OR previous_status IN ('PENDING','INTERVIEW_SCHEDULED','INTERVIEW_CONFIRMED','ACCEPTED','REJECTED')),
                new_status TEXT NOT NULL CHECK (new_status IN ('PENDING','INTERVIEW_SCHEDULED','INTERVIEW_CONFIRMED','ACCEPTED','REJECTED')),
                changed_by INTEGER NOT NULL,
                note TEXT CHECK (note IS NULL OR length(note)<=300),
                changed_at_ms INTEGER NOT NULL,
                FOREIGN KEY (application_id) REFERENCES club_applications(id) ON DELETE CASCADE,
                FOREIGN KEY (changed_by) REFERENCES users(id) ON DELETE RESTRICT
            )
            """,
            "CREATE INDEX IF NOT EXISTS ix_application_history_app_time ON application_status_history(application_id,changed_at_ms)",
            """
            CREATE TABLE IF NOT EXISTS club_events (
                id INTEGER PRIMARY KEY,
                club_id INTEGER NOT NULL,
                created_by INTEGER NOT NULL,
                title TEXT NOT NULL CHECK (length(trim(title)) BETWEEN 1 AND 120),
                description TEXT NOT NULL CHECK (length(trim(description)) BETWEEN 1 AND 2000),
                location TEXT NOT NULL CHECK (length(trim(location)) BETWEEN 1 AND 200),
                starts_at_ms INTEGER NOT NULL,
                ends_at_ms INTEGER NOT NULL,
                created_at_ms INTEGER NOT NULL,
                updated_at_ms INTEGER NOT NULL,
                is_active INTEGER NOT NULL DEFAULT 1 CHECK (is_active IN (0,1)),
                CHECK (ends_at_ms>starts_at_ms),
                FOREIGN KEY (club_id) REFERENCES clubs(id) ON DELETE RESTRICT,
                FOREIGN KEY (created_by,club_id) REFERENCES club_memberships(user_id,club_id) ON DELETE RESTRICT
            )
            """,
            "CREATE INDEX IF NOT EXISTS ix_events_active_start ON club_events(is_active,starts_at_ms,club_id)",
            """
            CREATE TRIGGER IF NOT EXISTS trg_application_must_start_pending
            BEFORE INSERT ON club_applications FOR EACH ROW
            WHEN NEW.status <> 'PENDING'
            BEGIN SELECT RAISE(ABORT,'Club applications must start as PENDING'); END
            """,
            """
            CREATE TRIGGER IF NOT EXISTS trg_valid_application_status_transition
            BEFORE UPDATE OF status ON club_applications FOR EACH ROW
            WHEN OLD.status<>NEW.status AND NOT (
                (OLD.status='PENDING' AND NEW.status IN ('INTERVIEW_SCHEDULED','REJECTED')) OR
                (OLD.status='INTERVIEW_SCHEDULED' AND NEW.status IN ('INTERVIEW_CONFIRMED','REJECTED')) OR
                (OLD.status='INTERVIEW_CONFIRMED' AND NEW.status IN ('ACCEPTED','REJECTED'))
            )
            BEGIN SELECT RAISE(ABORT,'Invalid club application status transition'); END
            """
    );

    private Schema() { }

    public static void create(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            for (String sql : DDL) statement.execute(sql);
        }
    }
}
