package com.kizuna.service;

import com.kizuna.db.Database;
import com.kizuna.model.Models.AdminApplication;
import com.kizuna.model.Models.ApplicationStatus;
import com.kizuna.model.Models.ApplicationSummary;
import com.kizuna.repository.ApplicationRepository;
import com.kizuna.repository.MembershipRepository;

import java.time.Instant;
import java.util.List;

public final class ApplicationService {
    private final Database database;
    private final Session session;
    private final AuthorizationService authorization;
    private final MembershipRepository memberships;
    private final ApplicationRepository applications;
    private final Runnable acceptanceHook;

    public ApplicationService(Database database, Session session, AuthorizationService authorization,
                              MembershipRepository memberships, ApplicationRepository applications) {
        this(database, session, authorization, memberships, applications, () -> { });
    }

    ApplicationService(Database database, Session session, AuthorizationService authorization,
                       MembershipRepository memberships, ApplicationRepository applications, Runnable acceptanceHook) {
        this.database = database; this.session = session; this.authorization = authorization;
        this.memberships = memberships; this.applications = applications; this.acceptanceHook = acceptanceHook;
    }

    public long apply(long clubId, String motivation, String experience, String availability) {
        long applicantId = session.requireUserId();
        String why = clean(motivation, 500, "Tell the club why you would like to join.");
        String background = optional(experience, 300, "Experience must be 300 characters or fewer.");
        String schedule = optional(availability, 300, "Availability must be 300 characters or fewer.");
        try {
            return database.transaction(c -> {
                if (memberships.isActiveMember(c, applicantId, clubId)) throw new IllegalStateException("You are already an active member of this club.");
                if (hasActiveApplication(c, applicantId, clubId)) throw new IllegalStateException("You already have an active application for this club.");
                long now = Instant.now().toEpochMilli();
                long id = applications.insert(c, applicantId, clubId, why, background, schedule, now);
                applications.addHistory(c, id, null, ApplicationStatus.PENDING, applicantId, "Application submitted", now);
                return id;
            });
        } catch (IllegalArgumentException | IllegalStateException e) { throw e; }
        catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("unique")) throw new IllegalStateException("You already have an active application for this club.");
            throw new IllegalStateException("Could not submit the application. Please try again.", e);
        }
    }

    public List<ApplicationSummary> myApplications() {
        long userId = session.requireUserId();
        try { return database.read(c -> applications.mine(c, userId)); }
        catch (Exception e) { throw new IllegalStateException("Could not load your applications.", e); }
    }

    public List<AdminApplication> applicationsForClub(long clubId) {
        try { return database.read(c -> { authorization.requireAdmin(c, clubId); return applications.forClub(c, clubId); }); }
        catch (SecurityException e) { throw e; }
        catch (Exception e) { throw new IllegalStateException("Could not load club applications.", e); }
    }

    public void scheduleInterview(long applicationId, long proposedAt, String location) {
        String place = clean(location, 200, "Enter an interview location.");
        long now = Instant.now().toEpochMilli();
        if (proposedAt <= now) throw new IllegalArgumentException("Choose an interview time in the future.");
        try {
            database.transaction(c -> {
                AdminApplication application = requireApplication(c, applicationId);
                long actor = authorization.requireAdmin(c, application.clubId());
                if (application.status() != ApplicationStatus.PENDING && application.status() != ApplicationStatus.INTERVIEW_SCHEDULED) {
                    throw new IllegalStateException("Only pending applications can be scheduled, and scheduled interviews can be changed.");
                }
                ApplicationStatus next = ApplicationStatus.INTERVIEW_SCHEDULED;
                applications.schedule(c, applicationId, actor, proposedAt, place, next, now);
                applications.addHistory(c, applicationId, application.status(), next, actor,
                        application.status() == next ? "Interview rescheduled" : "Interview proposed", now);
                return null;
            });
        } catch (IllegalArgumentException | IllegalStateException | SecurityException e) { throw e; }
        catch (Exception e) { throw new IllegalStateException("Could not schedule the interview.", e); }
    }

    public void confirmInterview(long applicationId) {
        long actor = session.requireUserId();
        long now = Instant.now().toEpochMilli();
        try {
            database.transaction(c -> {
                AdminApplication application = requireApplication(c, applicationId);
                if (application.applicantId() != actor) throw new SecurityException("You can only confirm your own interview.");
                if (application.status() != ApplicationStatus.INTERVIEW_SCHEDULED) throw new IllegalStateException("This application has no interview awaiting confirmation.");
                applications.confirm(c, applicationId, now);
                applications.addHistory(c, applicationId, ApplicationStatus.INTERVIEW_SCHEDULED, ApplicationStatus.INTERVIEW_CONFIRMED, actor, "Interview time confirmed", now);
                return null;
            });
        } catch (IllegalArgumentException | IllegalStateException | SecurityException e) { throw e; }
        catch (Exception e) { throw new IllegalStateException("Could not confirm the interview.", e); }
    }

    public void accept(long applicationId) { decide(applicationId, true); }
    public void reject(long applicationId) { decide(applicationId, false); }

    private void decide(long applicationId, boolean accept) {
        long now = Instant.now().toEpochMilli();
        try {
            database.transaction(c -> {
                AdminApplication application = requireApplication(c, applicationId);
                long actor = authorization.requireAdmin(c, application.clubId());
                ApplicationStatus next;
                if (accept) {
                    if (application.status() != ApplicationStatus.INTERVIEW_CONFIRMED) throw new IllegalStateException("Confirm the interview before accepting this application.");
                    next = ApplicationStatus.ACCEPTED;
                } else {
                    if (application.status() != ApplicationStatus.PENDING && application.status() != ApplicationStatus.INTERVIEW_SCHEDULED && application.status() != ApplicationStatus.INTERVIEW_CONFIRMED) {
                        throw new IllegalStateException("This application has already been reviewed.");
                    }
                    next = ApplicationStatus.REJECTED;
                }
                applications.review(c, applicationId, actor, next, now);
                if (accept) {
                    acceptanceHook.run();
                    memberships.addOrReactivateMember(c, application.applicantId(), application.clubId(), now);
                }
                applications.addHistory(c, applicationId, application.status(), next, actor,
                        accept ? "Application accepted" : "Application rejected", now);
                return null;
            });
        } catch (IllegalArgumentException | IllegalStateException | SecurityException e) { throw e; }
        catch (Exception e) { throw new IllegalStateException(accept ? "Could not accept the application." : "Could not reject the application.", e); }
    }

    private AdminApplication requireApplication(java.sql.Connection c, long id) throws Exception {
        AdminApplication application = applications.find(c, id);
        if (application == null) throw new IllegalArgumentException("Application was not found.");
        return application;
    }

    private static boolean hasActiveApplication(java.sql.Connection c, long applicant, long club) throws Exception {
        try (var ps = c.prepareStatement("SELECT 1 FROM club_applications WHERE applicant_id=? AND club_id=? AND status IN ('PENDING','INTERVIEW_SCHEDULED','INTERVIEW_CONFIRMED')")) {
            ps.setLong(1, applicant); ps.setLong(2, club); try (var rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    private static String clean(String value, int max, String emptyMessage) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) throw new IllegalArgumentException(emptyMessage);
        if (text.length() > max) throw new IllegalArgumentException("Text must be " + max + " characters or fewer.");
        return text;
    }
    private static String optional(String value, int max, String error) {
        String text = value == null ? "" : value.trim();
        if (text.length() > max) throw new IllegalArgumentException(error);
        return text.isEmpty() ? null : text;
    }
}
