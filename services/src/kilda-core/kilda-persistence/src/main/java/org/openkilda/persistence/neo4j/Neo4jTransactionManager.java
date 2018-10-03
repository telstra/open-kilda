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

package org.openkilda.persistence.neo4j;

import org.openkilda.persistence.PersistenceException;
import org.openkilda.persistence.TransactionManager;

import org.neo4j.ogm.config.Configuration.Builder;
import org.neo4j.ogm.session.Session;
import org.neo4j.ogm.session.SessionFactory;
import org.neo4j.ogm.transaction.Transaction;
import org.neo4j.ogm.transaction.Transaction.Status;

import java.util.Optional;

/**
 * Neo4J OGM implementation of TransactionManager. Used to manage transaction boundaries.
 */
public final class Neo4jTransactionManager implements TransactionManager, Neo4jSessionFactory {
    private static final ThreadLocal<Session> SESSION_HOLDER = new ThreadLocal<>();

    private final SessionFactory sessionFactory;

    public Neo4jTransactionManager(Neo4jConfig config) {
        Builder configBuilder = new Builder()
                .uri(config.getUri())
                .credentials(config.getLogin(), config.getPassword());
        if (config.getConnectionPoolSize() > 0) {
            configBuilder.connectionPoolSize(config.getConnectionPoolSize());
        }

        sessionFactory = new SessionFactory(configBuilder.build(), "org.openkilda.model");
        sessionFactory.metaData()
                .registerConversionCallback(new SimpleConversionCallback("org.openkilda.persistence.converters"));
    }

    /**
     * Get the existing session if there's an active transaction, otherwise create a new session.
     *
     * @return the session.
     */
    @Override
    public Session getSession() {
        Session session = SESSION_HOLDER.get();
        if (session != null) {
            return session;
        }
        return sessionFactory.openSession();
    }

    /**
     * Begin a new transaction.
     * <p/>
     * Only one transaction can be bound to a thread at any time, so calling this method
     * within an active transactions causes extending of transaction.
     * <p/>
     * See {@link org.neo4j.ogm.transaction.AbstractTransaction#extend}.
     */
    @Override
    public void begin() {
        Session session = SESSION_HOLDER.get();
        if (session == null) {
            session = sessionFactory.openSession();
            SESSION_HOLDER.set(session);
        }

        try {
            session.beginTransaction();
        } catch (Exception ex) {
            throw new PersistenceException("Unable to begin transaction.", ex);
        }
    }

    /**
     * Commit the existing transaction.
     */
    @Override
    public void commit() {
        Optional<Transaction> currentTx = Optional.ofNullable(SESSION_HOLDER.get())
                .map(Session::getTransaction);
        if (currentTx.isPresent()) {
            Transaction transaction = currentTx.get();
            try {
                transaction.commit();
                // Complete the transaction.
                transaction.close();
            } catch (Exception ex) {
                throw new PersistenceException("Unable to commit transaction.", ex);
            }

            if (transaction.status() == Status.COMMITTED || transaction.status() == Status.CLOSED) {
                // Release the session associated with the transaction and the current thread.
                SESSION_HOLDER.remove();
            }
        } else {
            throw new PersistenceException("Unable to commit transaction: there's no active Neo4j transaction.");
        }
    }

    /**
     * Rollback the existing transaction.
     */
    @Override
    public void rollback() {
        Optional<Transaction> currentTx = Optional.ofNullable(SESSION_HOLDER.get())
                .map(Session::getTransaction);
        if (currentTx.isPresent()) {
            Transaction transaction = currentTx.get();
            try {
                transaction.rollback();
                // Complete the transaction.
                transaction.close();
            } catch (Exception ex) {
                throw new PersistenceException("Unable to rollback transaction.", ex);
            }

            if (transaction.status() == Status.ROLLEDBACK || transaction.status() == Status.CLOSED) {
                // Release the session associated with the transaction and the current thread.
                SESSION_HOLDER.remove();
            }
        } else {
            throw new PersistenceException("Unable to rollback transaction: there's no active Neo4j transaction.");
        }
    }
}
