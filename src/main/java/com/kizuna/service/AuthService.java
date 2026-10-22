package com.kizuna.service;

import com.kizuna.db.Database;
import com.kizuna.model.Models.UserProfile;
import com.kizuna.repository.UserRepository;
import com.kizuna.util.AvatarStore;
import com.kizuna.util.PasswordHasher;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

public final class AuthService {
    private static final Set<String> YEARS = Set.of("1st Year", "2nd Year", "3rd Year", "4th Year", "5th Year", "Other");
    private static final String GENERIC_LOGIN_ERROR = "Username or password is incorrect.";

    private final Database database;
    private final UserRepository users;
    private final PasswordHasher hasher;
    private final AvatarStore avatars;
    private final Session session;
    private final String dummyHash;

    public AuthService(Database database, UserRepository users, PasswordHasher hasher, AvatarStore avatars, Session session) {
        this.database = database; this.users = users; this.hasher = hasher; this.avatars = avatars; this.session = session;
        char[] dummy = "not-a-real-account-password".toCharArray();
        try { dummyHash = hasher.hash(dummy); } finally { Arrays.fill(dummy, '\0'); }
    }

    public UserProfile login(String rawUsername, char[] password) {
        try {
            String username = normalizeUsername(rawUsername);
            UserRepository.Account account = database.read(c -> users.findAccount(c, username));
            boolean verified = hasher.verify(password, account == null ? dummyHash : account.passwordHash());
            if (!verified || account == null || !account.active()) throw new IllegalArgumentException(GENERIC_LOGIN_ERROR);
            session.signIn(account.profile());
            return account.profile();
        } catch (IllegalArgumentException e) {
            if (GENERIC_LOGIN_ERROR.equals(e.getMessage())) throw e;
            throw new IllegalArgumentException(GENERIC_LOGIN_ERROR);
        } catch (Exception e) {
            throw new IllegalStateException("Kizuna could not sign you in. Please try again.", e);
        } finally { Arrays.fill(password, '\0'); }
    }

    public UserProfile signup(String rawUsername, char[] password, String fullName, String academicYear, Path photo) {
        String photoFilename = null;
        try {
            String username = normalizeUsername(rawUsername);
            String name = fullName == null ? "" : fullName.trim();
            String year = academicYear == null ? "" : academicYear.trim();
            if (name.length() < 1 || name.length() > 80) throw new IllegalArgumentException("Enter a name from 1 to 80 characters.");
            if (!YEARS.contains(year)) throw new IllegalArgumentException("Choose an academic year.");
            if (password == null || password.length < 10 || password.length > 128) throw new IllegalArgumentException("Use a password between 10 and 128 characters.");
            if (photo != null) photoFilename = avatars.copy(photo);
            String hash = hasher.hash(password);
            UserProfile profile = usersInsert(username, hash, name, year, photoFilename);
            session.signIn(profile);
            return profile;
        } catch (IllegalArgumentException e) {
            if (photoFilename != null) avatars.delete(photoFilename);
            throw e;
        } catch (IOException e) {
            if (photoFilename != null) avatars.delete(photoFilename);
            throw new IllegalArgumentException(e.getMessage());
        } catch (Exception e) {
            if (photoFilename != null) avatars.delete(photoFilename);
            if (e instanceof SQLException && e.getMessage() != null && e.getMessage().toLowerCase(Locale.ROOT).contains("unique")) {
                throw new IllegalArgumentException("That username is already in use.");
            }
            throw new IllegalStateException("Kizuna could not create the account. Please try again.", e);
        } finally {
            if (password != null) Arrays.fill(password, '\0');
        }
    }

    public void logout() { session.clear(); }

    private UserProfile usersInsert(String username, String hash, String name, String year, String photo) throws Exception {
        return database.transaction(c -> users.insert(c, username, hash, name, year, photo, Instant.now().toEpochMilli()));
    }

    private static String normalizeUsername(String raw) {
        String username = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (username.length() < 3 || username.length() > 32) throw new IllegalArgumentException("Username must be 3 to 32 characters.");
        if (username.chars().anyMatch(Character::isWhitespace)) throw new IllegalArgumentException("Username cannot contain spaces.");
        return username;
    }
}
