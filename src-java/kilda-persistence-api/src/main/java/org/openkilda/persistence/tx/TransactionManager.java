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
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Manager of transaction boundaries.
 */
@Slf4j
public class TransactionManager implements Serializable {
    private static final ThreadLocal<Transaction> GLOBALS = new ThreadLocal<>();

    private final TransactionAdapterFactory adapterFactory;
    private final TransactionLayout layout;
    private final int transactionRetriesLimit;
    private final int transactionRetriesMaxDelay;

    public TransactionManager(
            TransactionLayout layout, TransactionAdapterFactory adapterFactory,
            int transactionRetriesLimit, int transactionRetriesMaxDelay) {
        this.layout = layout;
        this.adapterFactory = adapterFactory;
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
        Transaction adapters = GLOBALS.get();
        return adapters != null && adapters.size() > 0;
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
        TransactionAdapter adapter = adapterFactory.produce(layout);

        Transaction transaction = initAdapters(adapter);
        transaction.activate(adapter);
        try {
            T result = action.call();
            transaction.markSuccess();
            return result;
        } catch (Exception ex) {
            log.debug("Failed transaction in {} area in {}", adapter.getArea(), Thread.currentThread().getName(), ex);
            transaction.markFail();
            throw ex;
        } finally {
            close(transaction, adapter);
        }
    }

    /**
     * Close all active transaction/adapters/areas if it is root adapter.
     */
    private void close(Transaction transaction, TransactionAdapter current) throws Exception {
        String threadName = Thread.currentThread().getName();
        if (! transaction.canClose(current)) {
            log.debug("Do not close transaction into {}, because it is nested transaction block", threadName);
            return;
        }

        log.debug("Going to close all active transactions in {}", threadName);
        try {
            transaction.close();
        } finally {
            log.debug("Remove global transaction tracking in {}", threadName);
            GLOBALS.remove();
        }
    }

    private Transaction initAdapters(TransactionAdapter current) {
        Transaction adapters = GLOBALS.get();
        if (adapters == null) {
            adapters = new Transaction(current);
            log.debug("Install global transaction tracking in {}", Thread.currentThread().getName());
            GLOBALS.set(adapters);
        }
        return adapters;
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

    private static final class Transaction {
        private final TransactionAdapter root;

        private boolean success = false;
        private boolean fail = false;

        private final Set<TransactionArea> openAreas = new HashSet<>();
        private final LinkedList<TransactionAdapter> adapters = new LinkedList<>();

        private Transaction(TransactionAdapter root) {
            this.root = root;
        }

        public void activate(TransactionAdapter adapter) throws Exception {
            TransactionArea area = adapter.getArea();
            if (openAreas.add(area)) {
                log.debug(
                        "Going to open transaction for {} area in {}, using adapter {}",
                        area, Thread.currentThread().getName(), adapter);

                try {
                    adapter.open();
                    adapters.addFirst(adapter);  // the later it is open, the earlier it is closed
                } catch (Exception e) {
                    openAreas.remove(area);
                    throw e;
                }
            }
        }

        public void close() throws Exception {
            boolean canCommit = !fail && success;
            String closeAction = canCommit ? "commit" : "rollback";
            // TODO(surabujin): deny ability to open multiple transaction areas
            for (TransactionAdapter entry : adapters) {
                log.debug(
                        "Going to {} transaction for {} area in {}, using adapter {}",
                        closeAction, entry.getArea(), Thread.currentThread().getName(), entry);
                if (canCommit) {
                    entry.commit();
                } else {
                    entry.rollback();
                }
            }

            adapters.clear();
            openAreas.clear();
        }

        public void markSuccess() {
            success = true;
        }

        public void markFail() {
            fail = true;
        }

        public int size() {
            return openAreas.size();
        }

        public boolean canClose(TransactionAdapter current) {
            return root == current;
        }
    }
}
