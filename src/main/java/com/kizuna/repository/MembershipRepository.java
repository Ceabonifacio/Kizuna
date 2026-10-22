package com.kizuna.repository;

import com.kizuna.model.Models.Club;
import com.kizuna.model.Models.MemberProfile;
import com.kizuna.model.Models.NetworkClub;
import com.kizuna.model.Models.Role;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MembershipRepository {
    private final ClubRepository clubs;
    public MembershipRepository(ClubRepository clubs) { this.clubs = clubs; }

    public boolean isActiveLeader(Connection c, long userId, long clubId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM club_memberships m JOIN users u ON u.id=m.user_id WHERE m.user_id=? AND m.club_id=? AND m.status='ACTIVE' AND m.role IN ('PRESIDENT','VICE_PRESIDENT') AND u.is_active=1")) {
            ps.setLong(1, userId); ps.setLong(2, clubId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    public List<Club> administeredClubs(Connection c, long userId) throws SQLException {
        List<Club> result = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT c.id,c.name,c.description FROM clubs c JOIN club_memberships m ON m.club_id=c.id JOIN users u ON u.id=m.user_id WHERE m.user_id=? AND m.status='ACTIVE' AND m.role IN ('PRESIDENT','VICE_PRESIDENT') AND u.is_active=1 ORDER BY c.name COLLATE NOCASE")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) result.add(new Club(rs.getLong(1), rs.getString(2), rs.getString(3))); }
        }
        return result;
    }

    public List<NetworkClub> network(Connection c) throws SQLException {
        Map<Long, Club> clubMap = new LinkedHashMap<>();
        Map<Long, List<MemberProfile>> members = new LinkedHashMap<>();
        String sql = "SELECT c.id AS club_id,c.name AS club_name,c.description,u.id AS user_id,u.full_name,u.academic_year,u.photo_filename,m.role "
                + "FROM clubs c LEFT JOIN club_memberships m ON m.club_id=c.id AND m.status='ACTIVE' "
                + "LEFT JOIN users u ON u.id=m.user_id AND u.is_active=1 ORDER BY c.name COLLATE NOCASE,"
                + "CASE m.role WHEN 'PRESIDENT' THEN 0 WHEN 'VICE_PRESIDENT' THEN 1 ELSE 2 END,u.full_name COLLATE NOCASE";
        try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                long clubId = rs.getLong("club_id");
                clubMap.putIfAbsent(clubId, new Club(clubId, rs.getString("club_name"), rs.getString("description")));
                members.computeIfAbsent(clubId, ignored -> new ArrayList<>());
                long userId = rs.getLong("user_id");
                if (!rs.wasNull()) members.get(clubId).add(new MemberProfile(userId, rs.getString("full_name"), rs.getString("academic_year"), rs.getString("photo_filename"), Role.valueOf(rs.getString("role"))));
            }
        }
        List<NetworkClub> result = new ArrayList<>();
        clubMap.forEach((id, club) -> result.add(new NetworkClub(club, List.copyOf(members.get(id)))));
        return result;
    }

    public boolean isActiveMember(Connection c, long userId, long clubId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM club_memberships WHERE user_id=? AND club_id=? AND status='ACTIVE'")) {
            ps.setLong(1, userId); ps.setLong(2, clubId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    public List<MemberProfile> activeMembers(Connection c, long clubId) throws SQLException {
        List<MemberProfile> members = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT u.id,u.full_name,u.academic_year,u.photo_filename,m.role FROM club_memberships m JOIN users u ON u.id=m.user_id WHERE m.club_id=? AND m.status='ACTIVE' AND u.is_active=1 ORDER BY CASE m.role WHEN 'PRESIDENT' THEN 0 WHEN 'VICE_PRESIDENT' THEN 1 ELSE 2 END,u.full_name COLLATE NOCASE")) {
            ps.setLong(1, clubId);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) members.add(new MemberProfile(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), Role.valueOf(rs.getString(5)))); }
        }
        return members;
    }

    public void removeRegularMember(Connection c, long clubId, long userId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE club_memberships SET status='INACTIVE',updated_at_ms=? WHERE user_id=? AND club_id=? AND role='MEMBER' AND status='ACTIVE'")) {
            ps.setLong(1, Instant.now().toEpochMilli()); ps.setLong(2, userId); ps.setLong(3, clubId);
            if (ps.executeUpdate() != 1) throw new IllegalArgumentException("Only an active regular member of this club can be removed.");
        }
    }

    public void addOrReactivateMember(Connection c, long userId, long clubId, long now) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT role,status FROM club_memberships WHERE user_id=? AND club_id=?")) {
            ps.setLong(1, userId); ps.setLong(2, clubId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    if (!Role.MEMBER.name().equals(rs.getString("role"))) throw new IllegalStateException("This account already has a leadership role in the club.");
                    try (PreparedStatement update = c.prepareStatement("UPDATE club_memberships SET status='ACTIVE',updated_at_ms=? WHERE user_id=? AND club_id=?")) {
                        update.setLong(1, now); update.setLong(2, userId); update.setLong(3, clubId); update.executeUpdate();
                    }
                    return;
                }
            }
        }
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO club_memberships(user_id,club_id,role,status,joined_at_ms,updated_at_ms) VALUES(?,?,'MEMBER','ACTIVE',?,?)")) {
            ps.setLong(1, userId); ps.setLong(2, clubId); ps.setLong(3, now); ps.setLong(4, now); ps.executeUpdate();
        }
    }
}
