package com.kizuna.model;

import java.util.List;

public final class Models {
    private Models() { }

    public enum Role { MEMBER, PRESIDENT, VICE_PRESIDENT }
    public enum ApplicationStatus { PENDING, INTERVIEW_SCHEDULED, INTERVIEW_CONFIRMED, ACCEPTED, REJECTED }

    public record UserProfile(long id, String username, String fullName, String academicYear,
                              String photoFilename, Long assessmentCompletedAt) { }
    public record Club(long id, String name, String description) { }
    public record MemberProfile(long userId, String fullName, String academicYear,
                                String photoFilename, Role role) { }
    public record NetworkClub(Club club, List<MemberProfile> members) { }
    public record Option(long id, String key, String label) { }
    public record Question(long id, String key, String prompt, List<Option> options) { }
    public record Recommendation(long clubId, String clubName, String description, int score, String reason) { }
    public record AssessmentResult(List<Recommendation> recommendations, int clubsScored) { }
    public record ApplicationSummary(long id, long clubId, String clubName, ApplicationStatus status,
                                     long submittedAt, Long interviewAt, String interviewLocation) { }
    public record AdminApplication(long id, long clubId, String clubName, long applicantId,
                                   String applicantName, ApplicationStatus status, String motivation,
                                   String experience, String availability, long submittedAt,
                                   Long interviewAt, String interviewLocation) { }
    public record Event(long id, long clubId, String clubName, String title, String description,
                        String location, long startsAt, long endsAt, boolean active) { }
    public record EventForm(long clubId, String title, String description, String location,
                            long startsAt, long endsAt) { }
}
