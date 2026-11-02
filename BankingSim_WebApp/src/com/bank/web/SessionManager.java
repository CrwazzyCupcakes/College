package com.bank.web;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers who is logged in. After login the browser gets a random token and sends it
 * back in the X-Auth-Token header on every request. ConcurrentHashMap because many
 * request threads read and write the sessions at the same time.
 */
public class SessionManager {
    public static final String ROLE_CUSTOMER = "customer";
    public static final String ROLE_ADMIN = "admin";
    private static final long IDLE_TIMEOUT_MS = 30L * 60 * 1000; // 30 minutes

    public static final class Session {
        private final String role;
        private final String accountNumber; // null for admin
        private volatile long lastAccess = System.currentTimeMillis();

        Session(String role, String accountNumber) {
            this.role = role;
            this.accountNumber = accountNumber;
        }

        public String getRole() { return role; }
        public String getAccountNumber() { return accountNumber; }
    }

    private final Map<String, Session> sessions = new ConcurrentHashMap<String, Session>();
    private final SecureRandom random = new SecureRandom();

    public String create(String role, String accountNumber) {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        StringBuilder token = new StringBuilder();
        for (byte b : bytes) {
            token.append(String.format("%02x", b));
        }
        sessions.put(token.toString(), new Session(role, accountNumber));
        return token.toString();
    }

    /** Returns the session, or null if the token is unknown or has been idle too long. */
    public Session get(String token) {
        if (token == null) {
            return null;
        }
        Session session = sessions.get(token);
        if (session == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        if (now - session.lastAccess > IDLE_TIMEOUT_MS) {
            sessions.remove(token);
            return null;
        }
        session.lastAccess = now;
        return session;
    }

    public void remove(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }
}
