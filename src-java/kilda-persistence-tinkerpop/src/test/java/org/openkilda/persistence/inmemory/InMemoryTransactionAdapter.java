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

package org.openkilda.persistence.inmemory;

import org.openkilda.persistence.tx.TransactionAdapter;
import org.openkilda.persistence.tx.TransactionArea;

public class InMemoryTransactionAdapter extends TransactionAdapter {
    private static final ThreadLocal<Boolean> fakedTransactions = ThreadLocal.withInitial(() -> false);

    public InMemoryTransactionAdapter(TransactionArea area) {
        super(area);
    }

    public static boolean isFakedTxOpen() {
        return fakedTransactions.get();
    }

    @Override
    public void open() {
        fakedTransactions.set(true);
    }

    @Override
    public void commit() {
        fakedTransactions.set(false);
    }

    @Override
    public void rollback() {
        fakedTransactions.set(false);
    }
}
