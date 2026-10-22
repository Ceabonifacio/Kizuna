package com.kizuna.service;

import com.kizuna.repository.MembershipRepository;

import java.sql.Connection;
import java.sql.SQLException;

public final class AuthorizationService {
    private final Session session;
    private final MembershipRepository memberships;
    public AuthorizationService(Session session, MembershipRepository memberships) { this.session = session; this.memberships = memberships; }

    public long requireAdmin(Connection c, long clubId) throws SQLException {
        long actor = session.requireUserId();
        if (!memberships.isActiveLeader(c, actor, clubId)) throw new SecurityException("You do not administer this club.");
        return actor;
    }
}
