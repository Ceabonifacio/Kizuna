package com.kizuna.service;

import com.kizuna.model.Models.UserProfile;

import java.util.Optional;

public final class Session {
    private UserProfile user;

    public Optional<UserProfile> currentUser() { return Optional.ofNullable(user); }
    public long requireUserId() {
        if (user == null) throw new IllegalStateException("Please sign in to continue.");
        return user.id();
    }
    public void signIn(UserProfile profile) { this.user = profile; }
    public void refresh(UserProfile profile) { if (user != null && user.id() == profile.id()) this.user = profile; }
    public void clear() { user = null; }
}
