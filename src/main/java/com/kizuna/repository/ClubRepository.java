package com.kizuna.repository;

import com.kizuna.model.Models.Club;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class ClubRepository {
    public List<Club> list(Connection c) throws SQLException {
        List<Club> clubs = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT id,name,description FROM clubs ORDER BY name COLLATE NOCASE"); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) clubs.add(new Club(rs.getLong("id"), rs.getString("name"), rs.getString("description")));
        }
        return clubs;
    }

    public Club find(Connection c, long clubId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT id,name,description FROM clubs WHERE id=?")) {
            ps.setLong(1, clubId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? new Club(rs.getLong(1), rs.getString(2), rs.getString(3)) : null; }
        }
    }

    public Club findByName(Connection c, String name) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT id,name,description FROM clubs WHERE name=?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? new Club(rs.getLong(1), rs.getString(2), rs.getString(3)) : null; }
        }
    }
}
