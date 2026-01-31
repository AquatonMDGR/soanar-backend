package com.soanar.service.impl;

import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for handling retries with exponential backoff
 * Used by social media posting operations to recover from transient failures
 */
@Service
public class RetryService {

    private static final Logger logger = LoggerFactory.getLogger(RetryService.class);

    /**
     * Execute a supplier function with automatic retry
     * Retries up to 3 times with exponential backoff (1s, 2s, 4s)
     *
     * @param action The action to retry
     * @return The result from the supplier
     * @throws Exception if all retries fail
     */
    @Retryable(
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
    public <T> T executeWithRetry(RetryableAction<T> action) throws Exception {
        logger.debug("Executing action with retry capability");
        return action.execute();
    }

    /**
     * Execute a void action with automatic retry
     *
     * @param action The action to retry
     * @throws Exception if all retries fail
     */
    @Retryable(
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
    public void executeWithRetryVoid(RetryableVoidAction action) throws Exception {
        logger.debug("Executing void action with retry capability");
        action.execute();
    }

    /**
     * Functional interface for retryable actions that return a value
     */
    @FunctionalInterface
    public interface RetryableAction<T> {
        T execute() throws Exception;
    }

    /**
     * Functional interface for retryable void actions
     */
    @FunctionalInterface
    public interface RetryableVoidAction {
        void execute() throws Exception;
    }
}
