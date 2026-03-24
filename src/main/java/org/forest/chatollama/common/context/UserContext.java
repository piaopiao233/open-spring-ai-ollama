package org.forest.chatollama.common.context;

import org.forest.chatollama.dto.User;

public class UserContext {
    private static final ThreadLocal<User> USER_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<String> TOKEN_HOLDER = new ThreadLocal<>();

    public static void setUser(User user) {
        USER_HOLDER.set(user);
    }

    public static User getUser() {
        return USER_HOLDER.get();
    }

    public static void setToken(String token) {
        TOKEN_HOLDER.set(token);
    }

    public static String getToken() {
        return TOKEN_HOLDER.get();
    }

    public static void clear() {
        USER_HOLDER.remove();
        TOKEN_HOLDER.remove();
    }
}