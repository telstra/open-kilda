/* Copyright 2020 Telstra Open Source
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

import org.openkilda.persistence.PersistenceManager;

import lombok.Getter;

/**
 * A manager of persistence context. This can be used to check the context initialization, close the context, etc.
 */
public final class PersistenceContextManager {
    public static PersistenceContextManager INSTANCE = new PersistenceContextManager(null);

    private final ThreadLocal<PersistenceContext> globals = new ThreadLocal<>();

    @Getter
    private final PersistenceManager persistenceManager;

    public static void install(PersistenceManager persistenceManager) {
        INSTANCE = new PersistenceContextManager(persistenceManager);
    }

    private PersistenceContextManager(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    /**
     * Replace existing persistence context with a new one. Existing context will be correctly closed.
     */
    public PersistenceContext replace() {
        PersistenceContext current = globals.get();
        if (current != null) {
            current.close();
        }
        PersistenceContext change = new PersistenceContext(persistenceManager);
        globals.set(change);
        return change;
    }

    /**
     * Get current persistence context, create new if these are no context now.
     */
    public PersistenceContext getContextCreateIfMissing() {
        PersistenceContext entry = globals.get();
        if (entry == null) {
            entry = new PersistenceContext(persistenceManager);
            globals.set(entry);
        }
        return entry;
    }

    /**
     * Close persistence context.
     */
    public void close() {
        PersistenceContext entry = globals.get();
        if (entry == null) {
            return;
        }
        try {
            entry.close();
        } finally {
            globals.remove();
        }
    }

    public boolean isInitialized() {
        return globals.get() != null;
    }
}
