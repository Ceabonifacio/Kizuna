package com.kizuna.repository;

import com.kizuna.model.Models.UserProfile;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;

public final class UserRepository {
    public record Account(UserProfile profile, String passwordHash, boolean active) { }

    public Account findAccount(Connection c, String username) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT id,username,password_hash,full_name,academic_year,photo_filename,assessment_completed_at_ms,is_active FROM users WHERE username=?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                long completed = rs.getLong("assessment_completed_at_ms");
                Long assessmentAt = rs.wasNull() ? null : completed;
                UserProfile profile = new UserProfile(rs.getLong("id"), rs.getString("username"), rs.getString("full_name"),
                        rs.getString("academic_year"), rs.getString("photo_filename"), assessmentAt);
                return new Account(profile, rs.getString("password_hash"), rs.getInt("is_active") == 1);
            }
        }
    }

    public UserProfile findProfile(Connection c, long userId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT id,username,full_name,academic_year,photo_filename,assessment_completed_at_ms FROM users WHERE id=?")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                long complete = rs.getLong("assessment_completed_at_ms");
                Long completedAt = rs.wasNull() ? null : complete;
                return new UserProfile(rs.getLong("id"), rs.getString("username"), rs.getString("full_name"),
                        rs.getString("academic_year"), rs.getString("photo_filename"), completedAt);
            }
        }
    }

    public UserProfile insert(Connection c, String username, String passwordHash, String fullName,
                              String academicYear, String photoFilename, long createdAt) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO users(username,password_hash,full_name,academic_year,photo_filename,created_at_ms) VALUES(?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username); ps.setString(2, passwordHash); ps.setString(3, fullName);
            ps.setString(4, academicYear);
            if (photoFilename == null) ps.setNull(5, Types.VARCHAR); else ps.setString(5, photoFilename);
            ps.setLong(6, createdAt); ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Could not create the account.");
                return new UserProfile(keys.getLong(1), username, fullName, academicYear, photoFilename, null);
            }
        }
    }
}
