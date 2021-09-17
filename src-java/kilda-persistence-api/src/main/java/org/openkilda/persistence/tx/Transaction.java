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

package org.openkilda.persistence.tx;

import org.openkilda.persistence.PersistenceImplementationType;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Transaction {
    @Getter
    private final ImplementationTransactionAdapter<?> implementationTransactionAdapter;

    private boolean success = false;
    private boolean fail = false;

    @Getter
    private boolean active = false;

    public Transaction(ImplementationTransactionAdapter<?> implementationTransactionAdapter) {
        this.implementationTransactionAdapter = implementationTransactionAdapter;
    }

    /**
     * Activate/open persistence instance transaction adapter.
     */
    public void activate(ImplementationTransactionAdapter<?> effective) throws Exception {
        if (isRootTransaction(effective)) {
            log.debug(
                    "Going to open transaction for {} area in {}",
                    effective.getImplementationType(), Thread.currentThread().getName());
            effective.open();
            active = true;
        } else {
            if (implementationTransactionAdapter.getImplementationType() != effective.getImplementationType()) {
                throw new IllegalStateException(String.format(
                        "Attempt to use multiple persistence implementation in same transaction (%s and %s)",
                        implementationTransactionAdapter.getImplementationType(), effective.getImplementationType()));
            }
        }
    }

    /**
     * Close transaction if it is root transaction instance.
     */
    public boolean closeIfRoot(ImplementationTransactionAdapter<?> effective) throws Exception {
        if (! isRootTransaction(effective)) {
            return false;
        }

        boolean canCommit = !fail && success;
        String closeAction = canCommit ? "commit" : "rollback";
        log.debug(
                "Going to {} transaction for {} area in {}",
                closeAction, implementationTransactionAdapter.getImplementationType(),
                Thread.currentThread().getName());
        if (canCommit) {
            implementationTransactionAdapter.commit();
        } else {
            implementationTransactionAdapter.rollback();
        }

        return true;
    }

    public void markSuccess() {
        success = true;
    }

    public void markFail() {
        fail = true;
    }

    public PersistenceImplementationType getImplementationType() {
        return implementationTransactionAdapter.getImplementationType();
    }

    private boolean isRootTransaction(ImplementationTransactionAdapter<?> effective) {
        return implementationTransactionAdapter == effective;
    }
}
