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

package org.openkilda.persistence;

import org.openkilda.persistence.context.PersistenceContextManager;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.tx.TransactionArea;
import org.openkilda.persistence.tx.TransactionManager;

public class DummyPersistenceManager implements PersistenceManager {
    private final PersistenceContextManager contextManager = new DummyPersistenceContextManager();

    @Override
    public TransactionManager getTransactionManager() {
        throw new IllegalStateException(String.format(
                "Attempt to call \"getTransactionManager()\" on %s", getClass().getName()));
    }

    @Override
    public TransactionManager getTransactionManager(TransactionArea area) {
        throw new IllegalStateException(String.format(
                "Attempt to call \"getTransactionManager(%s)\" on %s", area, getClass().getName()));
    }

    @Override
    public RepositoryFactory getRepositoryFactory() {
        throw new IllegalStateException(String.format(
                "Attempt to call \"getRepositoryFactory()\" on %s", getClass().getName()));
    }

    @Override
    public PersistenceContextManager getPersistenceContextManager() {
        return contextManager;
    }
}
