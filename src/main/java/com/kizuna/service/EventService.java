package com.kizuna.service;

import com.kizuna.db.Database;
import com.kizuna.model.Models.Event;
import com.kizuna.model.Models.EventForm;
import com.kizuna.repository.EventRepository;

import java.time.Instant;
import java.util.List;

public final class EventService {
    private final Database database;
    private final Session session;
    private final AuthorizationService authorization;
    private final EventRepository events;

    public EventService(Database database, Session session, AuthorizationService authorization, EventRepository events) {
        this.database = database; this.session = session; this.authorization = authorization; this.events = events;
    }

    public List<Event> upcoming() {
        try { return database.read(c -> events.upcoming(c, Instant.now().toEpochMilli())); }
        catch (Exception e) { throw new IllegalStateException("Could not load upcoming events.", e); }
    }

    public List<Event> forClub(long clubId) {
        try { return database.read(c -> { authorization.requireAdmin(c, clubId); return events.forClub(c, clubId); }); }
        catch (SecurityException e) { throw e; }
        catch (Exception e) { throw new IllegalStateException("Could not load club events.", e); }
    }

    public long create(EventForm form) {
        validate(form);
        long now = Instant.now().toEpochMilli();
        try { return database.transaction(c -> { long actor = authorization.requireAdmin(c, form.clubId()); return events.create(c, actor, form.clubId(), clean(form.title()), clean(form.description()), clean(form.location()), form.startsAt(), form.endsAt(), now); }); }
        catch (IllegalArgumentException | SecurityException e) { throw e; }
        catch (Exception e) { throw new IllegalStateException("Could not save the event.", e); }
    }

    public void edit(long eventId, EventForm form) {
        validate(form);
        long now = Instant.now().toEpochMilli();
        try {
            database.transaction(c -> {
                Event existing = events.find(c, eventId);
                if (existing == null) throw new IllegalArgumentException("Event was not found.");
                if (existing.clubId() != form.clubId()) throw new SecurityException("An event cannot be moved to another club.");
                authorization.requireAdmin(c, existing.clubId());
                events.update(c, eventId, clean(form.title()), clean(form.description()), clean(form.location()), form.startsAt(), form.endsAt(), now);
                return null;
            });
        } catch (IllegalArgumentException | SecurityException e) { throw e; }
        catch (Exception e) { throw new IllegalStateException("Could not update the event.", e); }
    }

    public void deactivate(long eventId) {
        long now = Instant.now().toEpochMilli();
        try {
            database.transaction(c -> {
                Event existing = events.find(c, eventId);
                if (existing == null) throw new IllegalArgumentException("Event was not found.");
                authorization.requireAdmin(c, existing.clubId());
                events.deactivate(c, eventId, now);
                return null;
            });
        } catch (IllegalArgumentException | SecurityException e) { throw e; }
        catch (Exception e) { throw new IllegalStateException("Could not deactivate the event.", e); }
    }

    private static void validate(EventForm form) {
        if (form == null) throw new IllegalArgumentException("Enter the event details.");
        length(form.title(), 120, "Enter an event title (up to 120 characters).");
        length(form.description(), 2000, "Enter an event description (up to 2000 characters).");
        length(form.location(), 200, "Enter an event location (up to 200 characters).");
        if (form.startsAt() <= Instant.now().toEpochMilli()) throw new IllegalArgumentException("Choose an event start time in the future.");
        if (form.endsAt() <= form.startsAt()) throw new IllegalArgumentException("Event end must be after its start.");
    }
    private static void length(String value, int max, String error) {
        if (value == null || value.trim().isEmpty() || value.trim().length() > max) throw new IllegalArgumentException(error);
    }
    private static String clean(String value) { return value.trim(); }
}
