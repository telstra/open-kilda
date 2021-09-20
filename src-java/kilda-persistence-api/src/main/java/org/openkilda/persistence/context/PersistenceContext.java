/* Copyright 2021 Telstra Open Source
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

package org.openkilda.persistence.context;

import org.openkilda.persistence.PersistenceImplementation;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.exceptions.PersistenceException;
import org.openkilda.persistence.tx.Transaction;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Aimed to store data related to operations on persistence data (layer).
 *
 * <p>Context itself stores data related to the whole persistence layer (current transaction etc). Extensions are used
 * to store data related to specific implementations (database connection etc).
 */
@Slf4j
public class PersistenceContext {
    private final PersistenceManager persistenceManager;

    @Getter
    private Transaction transaction;

    private final Map<Class<?>, PersistentContextExtension> extensionByType = new HashMap<>();

    public PersistenceContext(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
        log.debug("Opening the persistence context in {}", Thread.currentThread().getName());
    }

    /**
     * Close context.
     */
    public void close() {
        String threadName = Thread.currentThread().getName();
        log.debug("Closing the persistence context in {}", threadName);
        if (isTxOpen()) {
            throw new PersistenceException(String.format(
                    "Closing the persistence context with active transaction in %s", threadName));
        }

        propagateCloseIntoImplementations();
    }

    public boolean isTxOpen() {
        return transaction != null && transaction.isActive();
    }

    /**
     * Save transaction instance if it's empty now.
     */
    public Transaction setTransactionIfClear(Supplier<Transaction> supplier) {
        if (transaction == null) {
            transaction = supplier.get();
        }
        return transaction;
    }

    public void clearTransaction() {
        transaction = null;
    }

    /**
     * Get context extension for specific persistence implementation.
     */
    public <T extends PersistentContextExtension> T getExtensionCreateIfMissing(
            Class<T> extensionType, Supplier<T> supplier) {
        PersistentContextExtension extension = extensionByType.computeIfAbsent(
                extensionType, dummy -> supplier.get());
        return extensionType.cast(extension);
    }

    private void propagateCloseIntoImplementations() {
        if (persistenceManager == null) {
            return;
        }
        for (PersistentContextExtension entry : extensionByType.values()) {
            PersistenceImplementation implementation = persistenceManager.getImplementation(
                    entry.getImplementationType());
            implementation.onContextClose(this);
        }
    }

    /**
     * Getter for persistenceManager field.
     */
    public PersistenceManager getPersistenceManager() {
        if (persistenceManager == null) {
            throw new IllegalStateException(String.format(
                    "%s have no %s object reference (persistence manger is not installed)",
                    getClass().getName(), PersistenceManager.class.getName()));
        }
        return persistenceManager;
    }
}
