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

import org.neo4j.ogm.session.Session;
import org.neo4j.ogm.transaction.Transaction;

import java.util.Optional;

/**
 * A simple transaction context holder. Designed to isolate transaction management from the implementation providers.
 */
public class Neo4jTransactionManager {
    public static final Neo4jTransactionManager INSTANCE = new Neo4jTransactionManager();

    private final Neo4jSessionHolder sessionHolder = Neo4jSessionHolder.INSTANCE;

    private Neo4jTransactionManager() {
    }

    /**
     * Begin a new transaction. If an existing transaction already exists, users must decide whether to commit or
     * rollback. Only one transaction can be bound to a thread at any time, so active transactions that have not been
     * closed but are no longer bound to the thread must be handled by client code.
     */
    public void beginTransaction() {
        if (!sessionHolder.hasSession()) {
            Session session = Neo4jSessionFactory.INSTANCE.openSession();
            session.beginTransaction();

            sessionHolder.setSession(session);
        } else {
            throw new PersistenceException("Unable to begin transaction: cannot start a transaction within a transaction.");
        }

    }

    /**
     * Commit the existing transaction.
     */
    public void commit() {
        Optional<Transaction> tx = Optional.ofNullable(sessionHolder.getSession()).map(Session::getTransaction);
        if (tx.isPresent()) {
            tx.ifPresent(Transaction::commit);
        } else {
            throw new PersistenceException("Unable to commit transaction: there's no active Neo4j transaction.");
        }
    }

    /**
     * Rollback the existing transaction.
     */
    public void rollback() {
        Optional<Transaction> tx = Optional.ofNullable(sessionHolder.getSession()).map(Session::getTransaction);
        if (tx.isPresent()) {
            tx.ifPresent(Transaction::rollback);
        } else {
            throw new PersistenceException("Unable to rollback transaction: there's no active Neo4j transaction.");
        }
    }

    /**
     * Close the existing transaction. The method releases the session associated with the current thread.
     */
    public void closeTransaction() {
        Optional<Session> session = Optional.ofNullable(sessionHolder.getSession());
        if (session.isPresent()) {
            session.map(Session::getTransaction).ifPresent(Transaction::close);
        } else {
            throw new PersistenceException("Unable to close transaction: there's no active Neo4j session.");
        }

        sessionHolder.clean();
    }
}
