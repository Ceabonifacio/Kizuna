package com.kizuna.repository;

import com.kizuna.model.Models.AdminApplication;
import com.kizuna.model.Models.ApplicationStatus;
import com.kizuna.model.Models.ApplicationSummary;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

public final class ApplicationRepository {
    public long insert(Connection c, long applicantId, long clubId, String motivation, String experience,
                       String availability, long now) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO club_applications(applicant_id,club_id,motivation,experience,availability_notes,status,submitted_at_ms,updated_at_ms) VALUES(?,?,?,?,?,'PENDING',?,?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, applicantId); ps.setLong(2, clubId); ps.setString(3, motivation); setNullable(ps, 4, experience); setNullable(ps, 5, availability);
            ps.setLong(6, now); ps.setLong(7, now); ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) { if (!keys.next()) throw new SQLException("Could not submit the application."); return keys.getLong(1); }
        }
    }

    public void addHistory(Connection c, long applicationId, ApplicationStatus previous, ApplicationStatus next,
                           long actorId, String note, long now) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO application_status_history(application_id,previous_status,new_status,changed_by,note,changed_at_ms) VALUES(?,?,?,?,?,?)")) {
            ps.setLong(1, applicationId);
            if (previous == null) ps.setNull(2, Types.VARCHAR); else ps.setString(2, previous.name());
            ps.setString(3, next.name()); ps.setLong(4, actorId); setNullable(ps, 5, note); ps.setLong(6, now); ps.executeUpdate();
        }
    }

    public AdminApplication find(Connection c, long applicationId) throws SQLException {
        String sql = "SELECT a.id,a.club_id,c.name AS club_name,a.applicant_id,u.full_name AS applicant_name,a.status,a.motivation,a.experience,a.availability_notes,a.submitted_at_ms,a.interview_at_ms,a.interview_location "
                + "FROM club_applications a JOIN clubs c ON c.id=a.club_id JOIN users u ON u.id=a.applicant_id WHERE a.id=?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, applicationId); try (ResultSet rs = ps.executeQuery()) { return rs.next() ? mapAdmin(rs) : null; }
        }
    }

    public List<ApplicationSummary> mine(Connection c, long applicantId) throws SQLException {
        List<ApplicationSummary> result = new ArrayList<>();
        String sql = "SELECT a.id,a.club_id,c.name,a.status,a.submitted_at_ms,a.interview_at_ms,a.interview_location FROM club_applications a JOIN clubs c ON c.id=a.club_id WHERE a.applicant_id=? ORDER BY a.submitted_at_ms DESC,a.id DESC";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, applicantId); try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(new ApplicationSummary(rs.getLong(1), rs.getLong(2), rs.getString(3), ApplicationStatus.valueOf(rs.getString(4)), rs.getLong(5), nullableLong(rs, 6), rs.getString(7)));
            }
        }
        return result;
    }

    public List<AdminApplication> forClub(Connection c, long clubId) throws SQLException {
        List<AdminApplication> result = new ArrayList<>();
        String sql = "SELECT a.id,a.club_id,c.name AS club_name,a.applicant_id,u.full_name AS applicant_name,a.status,a.motivation,a.experience,a.availability_notes,a.submitted_at_ms,a.interview_at_ms,a.interview_location "
                + "FROM club_applications a JOIN clubs c ON c.id=a.club_id JOIN users u ON u.id=a.applicant_id WHERE a.club_id=? ORDER BY a.submitted_at_ms DESC,a.id DESC";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, clubId); try (ResultSet rs = ps.executeQuery()) { while (rs.next()) result.add(mapAdmin(rs)); }
        }
        return result;
    }

    public void schedule(Connection c, long applicationId, long actorId, long interviewAt, String location,
                         ApplicationStatus newStatus, long now) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE club_applications SET status=?,interview_at_ms=?,interview_location=?,interview_scheduled_by=?,interview_confirmed_at_ms=NULL,updated_at_ms=? WHERE id=?")) {
            ps.setString(1, newStatus.name()); ps.setLong(2, interviewAt); ps.setString(3, location); ps.setLong(4, actorId); ps.setLong(5, now); ps.setLong(6, applicationId);
            if (ps.executeUpdate() != 1) throw new IllegalArgumentException("Application was not found.");
        }
    }

    public void confirm(Connection c, long applicationId, long now) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE club_applications SET status='INTERVIEW_CONFIRMED',interview_confirmed_at_ms=?,updated_at_ms=? WHERE id=?")) {
            ps.setLong(1, now); ps.setLong(2, now); ps.setLong(3, applicationId);
            if (ps.executeUpdate() != 1) throw new IllegalArgumentException("Application was not found.");
        }
    }

    public void review(Connection c, long applicationId, long actorId, ApplicationStatus status, long now) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE club_applications SET status=?,reviewed_by=?,reviewed_at_ms=?,updated_at_ms=? WHERE id=?")) {
            ps.setString(1, status.name()); ps.setLong(2, actorId); ps.setLong(3, now); ps.setLong(4, now); ps.setLong(5, applicationId);
            if (ps.executeUpdate() != 1) throw new IllegalArgumentException("Application was not found.");
        }
    }

    private static AdminApplication mapAdmin(ResultSet rs) throws SQLException {
        return new AdminApplication(rs.getLong("id"), rs.getLong("club_id"), rs.getString("club_name"),
                rs.getLong("applicant_id"), rs.getString("applicant_name"), ApplicationStatus.valueOf(rs.getString("status")),
                rs.getString("motivation"), rs.getString("experience"), rs.getString("availability_notes"),
                rs.getLong("submitted_at_ms"), nullableLong(rs, "interview_at_ms"), rs.getString("interview_location"));
    }

    private static Long nullableLong(ResultSet rs, int column) throws SQLException {
        long value = rs.getLong(column); return rs.wasNull() ? null : value;
    }
    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column); return rs.wasNull() ? null : value;
    }
    private static void setNullable(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null || value.isBlank()) ps.setNull(index, Types.VARCHAR); else ps.setString(index, value);
    }
}
