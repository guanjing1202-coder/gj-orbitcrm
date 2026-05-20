package com.orbitcrm.common.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class CurrentUserContext {
    private static final ThreadLocal<CurrentUser> CURRENT_USER = new ThreadLocal<CurrentUser>();

    private CurrentUserContext() {
    }

    public static void set(CurrentUser currentUser) {
        CURRENT_USER.set(currentUser);
    }

    public static CurrentUser get() {
        return CURRENT_USER.get();
    }

    public static CurrentUser require() {
        CurrentUser currentUser = CURRENT_USER.get();
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "authentication required");
        }
        return currentUser;
    }

    public static void clear() {
        CURRENT_USER.remove();
    }
}
