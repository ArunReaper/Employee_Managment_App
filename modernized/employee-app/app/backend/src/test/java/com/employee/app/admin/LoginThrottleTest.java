package com.employee.app.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** SEC-009: the lockout itself, without Spring. */
@DisplayName("SEC-009: LoginThrottle")
class LoginThrottleTest {

    // SEC-009 (review round 1): 50 parallel attempts from one address: exactly 5 get through
    @Test
    void sec009_parallelAttemptsCannotPassTheLimit() throws Exception {
        LoginThrottle throttle = new LoginThrottle();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(50);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return throttle.tryAcquire("10.0.0.1", null);
                }));
            }
            start.countDown();
            int allowed = 0;
            for (Future<Boolean> result : results) {
                allowed += result.get() ? 1 : 0;
            }
            assertThat(allowed).isEqualTo(5);
        } finally {
            pool.shutdownNow();
        }
    }

    // SEC-009: reset forgets both the address and the id
    @Test
    void sec009_resetForgetsAddressAndId() {
        LoginThrottle throttle = new LoginThrottle();
        for (int i = 0; i < 4; i++) {
            assertThat(throttle.tryAcquire("10.0.0.1", 7)).isTrue();
        }
        throttle.reset("10.0.0.1", 7);

        for (int i = 0; i < 5; i++) {
            assertThat(throttle.tryAcquire("10.0.0.1", 7)).isTrue();
        }
        assertThat(throttle.tryAcquire("10.0.0.1", 7)).isFalse();
    }
}
