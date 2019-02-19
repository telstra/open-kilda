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

package org.openkilda.persistence;

import org.openkilda.persistence.repositories.impl.Neo4jSessionFactory;

import com.google.common.annotations.VisibleForTesting;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.ogm.session.Session;
import org.neo4j.ogm.session.SessionFactory;
import org.neo4j.ogm.transaction.Transaction;
import org.neo4j.ogm.transaction.Transaction.Status;

import java.util.Optional;

/**
 * Neo4J OGM implementation of {@link TransactionManager}. Manages transaction boundaries.
 */
@Slf4j
final class Neo4jTransactionManager implements TransactionManager, Neo4jSessionFactory {
    private static final ThreadLocal<Session> SESSION_HOLDER = new ThreadLocal<>();

    private final SessionFactory sessionFactory;

    public Neo4jTransactionManager(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    /**
     * Get the existing session if there's an active transaction, otherwise create a new session.
     *
     * @return the session.
     */
    @Override
    public Session getSession() {
        return Optional.ofNullable(SESSION_HOLDER.get()).orElseGet(sessionFactory::openSession);
    }

    @SneakyThrows
    @Override
    public <T, E extends Exception> T doInTransaction(TransactionCallback<T, E> action) {
        begin();

        try {
            T result = action.doInTransaction();
            commit();
            return result;
        } catch (Exception ex) {
            rollback();
            throw ex;
        }
    }

    @SneakyThrows
    @Override
    public <E extends Exception> void doInTransaction(TransactionCallbackWithoutResult<E> action) {
        begin();

        try {
            action.doInTransaction();
            commit();
        } catch (Exception ex) {
            rollback();
            throw ex;
        }
    }

    /**
     * Begin a new transaction.
     * <p/>
     * Only one transaction can be bound to a thread at any time, so calling this method within an active transactions
     * causes extending of transaction.
     * <p/>
     * See {@link org.neo4j.ogm.transaction.AbstractTransaction#extend}.
     */
    @VisibleForTesting
    void begin() {
        Session session = getSession();

        try {
            session.beginTransaction();
        } catch (Exception ex) {
            throw new PersistenceException("Unable to begin transaction.", ex);
        }

        SESSION_HOLDER.set(session);
    }

    /**
     * Commit the existing transaction.
     */
    @VisibleForTesting
    void commit() {
        Optional<Transaction> currentTx = Optional.ofNullable(SESSION_HOLDER.get())
                .map(Session::getTransaction);
        Transaction transaction = currentTx.orElseThrow(
                () -> new PersistenceException("Unable to commit transaction: there's no active Neo4j transaction."));

        try {
            transaction.commit();
            // Complete the transaction.
            transaction.close();
        } catch (Exception ex) {
            // We don't close the transaction on any failure
            // as it's up to a consumer to decide what to do with the failed transaction:
            // rollback it or retry commit.
            throw new PersistenceException("Unable to commit transaction.", ex);
        } finally {
            if (transaction.status() == Status.COMMITTED || transaction.status() == Status.CLOSED) {
                // Release the session associated with the transaction and the current thread.
                SESSION_HOLDER.remove();
            }
        }
    }

    /**
     * Rollback the existing transaction.
     */
    @VisibleForTesting
    void rollback() {
        Optional<Transaction> currentTx = Optional.ofNullable(SESSION_HOLDER.get())
                .map(Session::getTransaction);
        Transaction transaction = currentTx.orElseThrow(
                () -> new PersistenceException("Unable to commit transaction: there's no active Neo4j transaction."));

        try {
            transaction.rollback();
        } catch (Exception ex) {
            throw new PersistenceException("Unable to rollback transaction.", ex);
        } finally {
            // Enfoce closing of the transaction as a consumer already decided to abandon it.
            try {
                transaction.close();
            } catch (Exception ex) {
                log.error("Unable to close transaction: {}", transaction, ex);
            }

            if (transaction.status() == Status.ROLLEDBACK || transaction.status() == Status.CLOSED) {
                // Release the session associated with the transaction and the current thread.
                SESSION_HOLDER.remove();
            }
        }
    }
}
