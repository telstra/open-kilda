/* Copyright 2018 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.persistence.tx;

import org.openkilda.persistence.PersistenceImplementation;
import org.openkilda.persistence.context.PersistenceContext;
import org.openkilda.persistence.context.PersistenceContextManager;
import org.openkilda.persistence.context.PersistenceContextRequired;
import org.openkilda.persistence.exceptions.PersistenceException;
import org.openkilda.persistence.exceptions.RecoverablePersistenceException;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.FailsafeException;
import net.jodah.failsafe.RetryPolicy;

import java.io.Serializable;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Callable;

/**
 * Manager of transaction boundaries.
 */
@Slf4j
public class TransactionManager implements Serializable {
    private final PersistenceImplementation implementation;

    private final int transactionRetriesLimit;
    private final int transactionRetriesMaxDelay;

    public TransactionManager(
            PersistenceImplementation implementation, int transactionRetriesLimit, int transactionRetriesMaxDelay) {
        this.implementation = implementation;

        this.transactionRetriesLimit = transactionRetriesLimit;
        this.transactionRetriesMaxDelay = transactionRetriesMaxDelay;
    }

    /**
     * Execute the action specified by the given callback within a transaction.
     * <p/>
     * A RuntimeException thrown by the callback is treated as a fatal exception that enforces a rollback.
     * The exception is propagated to the caller.
     *
     * @param action the transactional action
     * @return a result returned by the callback
     */
    @SneakyThrows
    public <T, E extends Throwable> T doInTransaction(TransactionCallback<T, E> action) throws E {
        if (isTxOpen()) {
            return execute(callableOf(action));
        } else {
            return execute(getDefaultRetryPolicy(), callableOf(action));
        }
    }

    /**
     * Run lambda inside transaction (retry execution according to settings into retry policy).
     */
    @SneakyThrows
    public <T, E extends Throwable> T doInTransaction(RetryPolicy<T> retryPolicy, TransactionCallback<T, E> action)
            throws E {
        if (isTxOpen()) {
            throw new PersistenceException("Nested transaction mustn't be retryable");
        }
        return execute(retryPolicy, callableOf(action));
    }

    /**
     * Execute the action specified by the given callback within a transaction.
     * <p/>
     * A RuntimeException thrown by the callback is treated as a fatal exception that enforces a rollback.
     * The exception is propagated to the caller.
     *
     * @param action the transactional action
     */
    @SneakyThrows
    public <E extends Throwable> void doInTransaction(TransactionCallbackWithoutResult<E> action) throws E {
        if (isTxOpen()) {
            execute(callableOf(action));
        } else {
            execute(getDefaultRetryPolicy(), callableOf(action));
        }
    }

    /**
     * Run lambda inside transaction (retry execution according to settings into retry policy).
     */
    @SneakyThrows
    public <E extends Throwable> void doInTransaction(
            RetryPolicy<?> retryPolicy, TransactionCallbackWithoutResult<E> action) throws E {
        if (isTxOpen()) {
            throw new PersistenceException("Nested transaction mustn't be retryable");
        }
        execute(retryPolicy, callableOf(action));
    }

    /**
     * Create retry policy using knowledge about possible transient exceptions produced by specific persistence layer.
     */
    public <T> RetryPolicy<T> getDefaultRetryPolicy() {
        RetryPolicy<T> retryPolicy = new RetryPolicy<T>()
                .handle(RecoverablePersistenceException.class)
                .withMaxRetries(transactionRetriesLimit)
                .onRetry(e -> log.info("Failure in transaction. Retrying #{}...", e.getAttemptCount(),
                        e.getLastFailure()))
                .onRetriesExceeded(e -> log.error("Failure in transaction. No more retries", e.getFailure()));
        if (transactionRetriesMaxDelay > 0) {
            retryPolicy.withBackoff(1, transactionRetriesMaxDelay, ChronoUnit.MILLIS);
        }
        return retryPolicy;
    }

    public boolean isTxOpen() {
        PersistenceContext context = PersistenceContextManager.INSTANCE.getContextCreateIfMissing();
        return context.isTxOpen();
    }

    @SneakyThrows
    private <T> T execute(RetryPolicy<T> retryPolicy, Callable<T> action) {
        try {
            return Failsafe.with(retryPolicy)
                    .get(() -> execute(action));
        } catch (FailsafeException ex) {
            throw ex.getCause();
        }
    }

    @PersistenceContextRequired
    protected <T> T execute(Callable<T> action) throws Exception {
        ImplementationTransactionAdapter<?> implementationTransactionAdapter = implementation.newTransactionAdapter();

        Transaction transaction = fillContext(implementationTransactionAdapter);
        try {
            transaction.activate(implementationTransactionAdapter);
        } catch (Exception e) {
            clearContext();
            throw e;
        }

        try {
            T result = action.call();
            transaction.markSuccess();
            return result;
        } catch (Exception ex) {
            log.debug("Failed transaction in {} area in {}",
                    implementationTransactionAdapter.getImplementationType(), Thread.currentThread().getName(), ex);
            transaction.markFail();
            throw ex;
        } finally {
            close(transaction, implementationTransactionAdapter);
        }
    }

    /**
     * Close all active transaction if effective transaction is root transaction.
     */
    private void close(Transaction transaction, ImplementationTransactionAdapter<?> implementationTransactionAdapter)
            throws Exception {
        String threadName = Thread.currentThread().getName();
        boolean canClear = true;
        try {
            canClear = transaction.closeIfRoot(implementationTransactionAdapter);
        } finally {
            if (canClear) {
                log.debug("Remove global transaction tracking in {}", threadName);
                clearContext();
            } else {
                log.debug("Do not close transaction into {}, because it is nested transaction block", threadName);
            }
        }
    }

    private Transaction fillContext(ImplementationTransactionAdapter<?> effective) {
        PersistenceContext context = PersistenceContextManager.INSTANCE.getContextCreateIfMissing();
        return context.setTransactionIfClear(() -> {
            log.debug("Install global transaction tracking in {}", Thread.currentThread().getName());
            return new Transaction(effective);
        });
    }

    private void clearContext() {
        log.debug("Remove global transaction tracking in {}", Thread.currentThread().getName());
        PersistenceContext context = PersistenceContextManager.INSTANCE.getContextCreateIfMissing();
        context.clearTransaction();
    }

    private <T, E extends Throwable> Callable<T> callableOf(TransactionCallback<T, E> action) {
        return action::doInTransaction;
    }

    private <T, E extends Throwable> Callable<T> callableOf(TransactionCallbackWithoutResult<E> action) {
        return () -> {
            action.doInTransaction();
            return null;
        };
    }
}
