package com.kizuna.service;

import com.kizuna.db.Database;
import com.kizuna.model.Models.Club;
import com.kizuna.model.Models.MemberProfile;
import com.kizuna.model.Models.NetworkClub;
import com.kizuna.repository.MembershipRepository;

import java.util.List;

public final class MembershipService {
    private final Database database;
    private final Session session;
    private final AuthorizationService authorization;
    private final MembershipRepository memberships;

    public MembershipService(Database database, Session session, AuthorizationService authorization, MembershipRepository memberships) {
        this.database = database; this.session = session; this.authorization = authorization; this.memberships = memberships;
    }

    public List<Club> administeredClubs() {
        try { return database.read(c -> memberships.administeredClubs(c, session.requireUserId())); }
        catch (Exception e) { throw new IllegalStateException("Could not load clubs you administer.", e); }
    }

    public List<NetworkClub> network() {
        try { return database.read(memberships::network); }
        catch (Exception e) { throw new IllegalStateException("Could not load the club network.", e); }
    }

    public List<MemberProfile> activeMembers(long clubId) {
        try {
            return database.read(c -> {
                if (!memberships.isActiveLeader(c, session.requireUserId(), clubId)) throw new SecurityException("You do not administer this club.");
                return memberships.activeMembers(c, clubId);
            });
        } catch (SecurityException e) { throw e; }
        catch (Exception e) { throw new IllegalStateException("Could not load club members.", e); }
    }

    public void removeMember(long clubId, long userId) {
        try { database.transaction(c -> { authorization.requireAdmin(c, clubId); memberships.removeRegularMember(c, clubId, userId); return null; }); }
        catch (SecurityException | IllegalArgumentException e) { throw e; }
        catch (Exception e) { throw new IllegalStateException("Could not remove this member.", e); }
    }
}
