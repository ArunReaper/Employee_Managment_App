package com.employee.app.support;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Counts test cases that actually executed (each parameterized invocation counts once) and prints
 * "equivalence cases executed: N" after every test class. Zero executed means nothing was proven.
 */
public class ExecutedCaseCounter implements AfterTestExecutionCallback, AfterAllCallback {

    private static final AtomicInteger EXECUTED = new AtomicInteger();
    private static final AtomicInteger PASSED = new AtomicInteger();

    @Override
    public void afterTestExecution(ExtensionContext context) {
        EXECUTED.incrementAndGet();
        if (context.getExecutionException().isEmpty()) {
            PASSED.incrementAndGet();
        }
    }

    @Override
    public void afterAll(ExtensionContext context) {
        System.out.printf("equivalence cases executed: %d (passed: %d) after %s%n",
                EXECUTED.get(), PASSED.get(), context.getDisplayName());
    }
}
