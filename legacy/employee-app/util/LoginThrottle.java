package com.employee.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Allows at most 5 login attempts per key in a row, then locks that key for 15 minutes.
 * Callers reserve an attempt with tryAcquire() BEFORE checking the credentials, so parallel
 * requests cannot all slip past the limit, and call reset() only after a successful login.
 * Counts are kept per client address ("ip:") and per admin id ("id:"); an id lock also shuts
 * out its real owner for 15 minutes, which is the price of stopping guesses spread over many addresses.
 * Deployment assumption: getRemoteAddr() is the real client. Behind a reverse proxy, configure
 * Tomcat's RemoteIpValve with a narrow trusted-proxy list, or every user shares the proxy's address.
 * At most 10,000 keys are tracked; when the table is full of active keys, new keys are refused
 * (fails closed) until idle ones expire. State is in memory, per Tomcat instance, and is lost on restart.
 */
public final class LoginThrottle {
    private static final Logger LOG = Logger.getLogger(LoginThrottle.class.getName());
    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCK_MILLIS = 15 * 60 * 1000L;
    private static final int MAX_TRACKED = 10_000;
    private static final long SWEEP_EVERY_MILLIS = 60 * 1000L;

    private static final Map<String, Attempts> ATTEMPTS = new ConcurrentHashMap<>();
    private static volatile long lastSweep;

    private LoginThrottle() {
    }

    /** Reserves one attempt for the key; false means the key is locked and the login must be refused. */
    private static boolean tryAcquire(String key) {
        long now = System.currentTimeMillis();
        Attempts attempts = ATTEMPTS.get(key);
        if (attempts == null) {
            sweepIfFull(now);
            if (ATTEMPTS.size() >= MAX_TRACKED) {
                return false;
            }
            attempts = ATTEMPTS.computeIfAbsent(key, k -> new Attempts());
        }
        return attempts.tryAcquire(now, key);
    }

    /**
     * Reserves an attempt for both the client address and the admin id (pass the parsed number, or
     * null when the id is not a number, so "1", " 1" and "01" share one count); refuses if either is locked.
     */
    public static boolean tryAcquire(String clientAddress, Integer adminId) {
        // A locked address must not add to an id's count, or one address could lock out every admin
        if (!tryAcquire("ip:" + clientAddress)) {
            return false;
        }
        return adminId == null || tryAcquire("id:" + adminId);
    }

    public static void reset(String clientAddress, Integer adminId) {
        ATTEMPTS.remove("ip:" + clientAddress);
        ATTEMPTS.remove("id:" + adminId);
    }

    // Drops keys idle for longer than the lock window, at most once a minute, once the table is full
    private static void sweepIfFull(long now) {
        if (ATTEMPTS.size() >= MAX_TRACKED && now - lastSweep >= SWEEP_EVERY_MILLIS) {
            lastSweep = now;
            ATTEMPTS.values().removeIf(a -> a.isIdle(now));
        }
    }

    private static final class Attempts {
        private int count;
        private long lastAttempt;
        private long lockedUntil;

        synchronized boolean tryAcquire(long now, String key) {
            if (now < lockedUntil) {
                return false;
            }
            if (now - lastAttempt >= LOCK_MILLIS) {
                count = 0; // the last streak is older than the window (or a lock just expired): start over
            }
            lastAttempt = now;
            if (++count >= MAX_ATTEMPTS) {
                lockedUntil = now + LOCK_MILLIS;
                LOG.warning("Login locked for 15 minutes after " + MAX_ATTEMPTS + " attempts: " + key);
            }
            return true;
        }

        synchronized boolean isIdle(long now) {
            return now >= lockedUntil && now - lastAttempt >= LOCK_MILLIS;
        }
    }
}
