package com.microinvent.common.context;

public final class UserContextHolder {

    private static final ThreadLocal<UserContext> CURRENT = new ThreadLocal<>();

    private UserContextHolder() {
    }

    public static UserContext get() {
        UserContext ctx = CURRENT.get();
        return ctx != null ? ctx : UserContext.ANONYMOUS;
    }

    public static void set(UserContext ctx) {
        CURRENT.set(ctx);
    }

    public static void clear() {
        CURRENT.remove();
    }
}
