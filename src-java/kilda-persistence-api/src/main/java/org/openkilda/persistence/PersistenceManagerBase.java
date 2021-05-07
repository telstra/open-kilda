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

import org.openkilda.config.provider.ConfigurationProvider;
import org.openkilda.persistence.tx.TransactionArea;
import org.openkilda.persistence.tx.TransactionManager;

import java.util.HashMap;
import java.util.Map;

public abstract class PersistenceManagerBase implements PersistenceManager {
    private final Map<TransactionArea, TransactionManager> transactionManagerByArea = new HashMap<>();

    public PersistenceManagerBase(ConfigurationProvider configurationProvider) {
        // just force constructor argument for inheritors
    }

    @Override
    public TransactionManager getTransactionManager(TransactionArea area) {
        synchronized (transactionManagerByArea) {
            return transactionManagerByArea.computeIfAbsent(area, this::makeTransactionManager);
        }
    }

    protected abstract TransactionManager makeTransactionManager(TransactionArea area);
}
