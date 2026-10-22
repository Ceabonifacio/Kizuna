package com.kizuna.repository;

import com.kizuna.model.Models.Event;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public final class EventRepository {
    public List<Event> upcoming(Connection c, long now) throws SQLException {
        return query(c, "WHERE e.is_active=1 AND e.starts_at_ms>=?", now, null);
    }

    public List<Event> forClub(Connection c, long clubId) throws SQLException {
        return query(c, "WHERE e.club_id=?", clubId, null);
    }

    public Event find(Connection c, long eventId) throws SQLException {
        List<Event> rows = query(c, "WHERE e.id=?", eventId, null);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public long create(Connection c, long actor, long clubId, String title, String description, String location,
                       long starts, long ends, long now) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO club_events(club_id,created_by,title,description,location,starts_at_ms,ends_at_ms,created_at_ms,updated_at_ms) VALUES(?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, clubId); ps.setLong(2, actor); ps.setString(3, title); ps.setString(4, description); ps.setString(5, location);
            ps.setLong(6, starts); ps.setLong(7, ends); ps.setLong(8, now); ps.setLong(9, now); ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) { if (!keys.next()) throw new SQLException("Could not create the event."); return keys.getLong(1); }
        }
    }

    public void update(Connection c, long eventId, String title, String description, String location,
                       long starts, long ends, long now) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE club_events SET title=?,description=?,location=?,starts_at_ms=?,ends_at_ms=?,updated_at_ms=? WHERE id=? AND is_active=1")) {
            ps.setString(1, title); ps.setString(2, description); ps.setString(3, location); ps.setLong(4, starts); ps.setLong(5, ends); ps.setLong(6, now); ps.setLong(7, eventId);
            if (ps.executeUpdate() != 1) throw new IllegalArgumentException("Active event was not found.");
        }
    }

    public void deactivate(Connection c, long eventId, long now) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE club_events SET is_active=0,updated_at_ms=? WHERE id=? AND is_active=1")) {
            ps.setLong(1, now); ps.setLong(2, eventId);
            if (ps.executeUpdate() != 1) throw new IllegalArgumentException("Active event was not found.");
        }
    }

    private static List<Event> query(Connection c, String clause, long first, Long second) throws SQLException {
        List<Event> events = new ArrayList<>();
        String sql = "SELECT e.id,e.club_id,c.name AS club_name,e.title,e.description,e.location,e.starts_at_ms,e.ends_at_ms,e.is_active FROM club_events e JOIN clubs c ON c.id=e.club_id " + clause + " ORDER BY e.starts_at_ms,e.title COLLATE NOCASE";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, first); if (second != null) ps.setLong(2, second);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) events.add(new Event(rs.getLong("id"), rs.getLong("club_id"), rs.getString("club_name"), rs.getString("title"),
                        rs.getString("description"), rs.getString("location"), rs.getLong("starts_at_ms"), rs.getLong("ends_at_ms"), rs.getInt("is_active") == 1));
            }
        }
        return List.copyOf(events);
    }
}
