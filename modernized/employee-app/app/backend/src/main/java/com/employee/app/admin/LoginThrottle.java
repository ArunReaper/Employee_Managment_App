package com.employee.app.admin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Login lockout, ported from the hardened legacy util/LoginThrottle (SECURITY_FINDINGS.md SEC-009).
 * At most 5 attempts in a row per key, then the key is locked for 15 minutes. An attempt is reserved
 * BEFORE the credentials are checked, so parallel requests cannot pass the limit; reset() only on success.
 * Keys: the client address ("ip:") and the admin id ("id:"); a locked address never adds to an id's count.
 * An id lock also shuts out its real owner for 15 minutes: the price of stopping guesses spread over many
 * addresses. Deployment assumption: getRemoteAddr() is the real client (behind a proxy, set
 * server.forward-headers-strategy with a narrow trusted-proxy list). At most 10,000 keys are tracked; when
 * the table is full of active keys, new keys are refused (fails closed). In memory, per instance.
 */
@Component
public class LoginThrottle {

    static final String LOCKED = "Too many failed attempts. Please try again in 15 minutes.";

    private static final Logger log = LoggerFactory.getLogger(LoginThrottle.class);
    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCK_MILLIS = 15 * 60 * 1000L;
    private static final int MAX_TRACKED = 10_000;
    private static final long SWEEP_EVERY_MILLIS = 60 * 1000L;

    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();
    private volatile long lastSweep;

    /** Reserves an attempt for the address, then for the id (null when it is not a number); false = refuse. */
    public boolean tryAcquire(String clientAddress, Integer adminId) {
        if (!tryAcquire("ip:" + clientAddress)) {
            return false;
        }
        return adminId == null || tryAcquire("id:" + adminId);
    }

    public void reset(String clientAddress, Integer adminId) {
        attempts.remove("ip:" + clientAddress);
        attempts.remove("id:" + adminId);
    }

    /** Forgets every count; for tests, which share one application context. */
    public void clear() {
        attempts.clear();
    }

    private boolean tryAcquire(String key) {
        long now = System.currentTimeMillis();
        Attempts entry = attempts.get(key);
        if (entry == null) {
            sweepIfFull(now);
            if (attempts.size() >= MAX_TRACKED) {
                return false;
            }
            entry = attempts.computeIfAbsent(key, k -> new Attempts());
        }
        return entry.tryAcquire(now, key);
    }

    // Drops keys idle for longer than the lock window, at most once a minute, once the table is full
    private void sweepIfFull(long now) {
        if (attempts.size() >= MAX_TRACKED && now - lastSweep >= SWEEP_EVERY_MILLIS) {
            lastSweep = now;
            attempts.values().removeIf(a -> a.isIdle(now));
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
                log.warn("Login locked for 15 minutes after {} attempts: {}", MAX_ATTEMPTS, key);
            }
            return true;
        }

        synchronized boolean isIdle(long now) {
            return now >= lockedUntil && now - lastAttempt >= LOCK_MILLIS;
        }
    }
}
