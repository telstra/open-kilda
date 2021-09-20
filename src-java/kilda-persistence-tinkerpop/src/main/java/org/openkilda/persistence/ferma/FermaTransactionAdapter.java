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

package org.openkilda.persistence.ferma;

import org.openkilda.persistence.context.PersistenceContext;
import org.openkilda.persistence.context.PersistenceContextManager;
import org.openkilda.persistence.exceptions.PersistenceException;
import org.openkilda.persistence.tx.ImplementationTransactionAdapter;

import com.syncleus.ferma.DelegatingFramedGraph;
import com.syncleus.ferma.WrappedTransaction;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FermaTransactionAdapter<T extends FermaPersistentImplementation>
        extends ImplementationTransactionAdapter<T> {
    public FermaTransactionAdapter(T implementation) {
        super(implementation);
    }

    @Override
    public void open() throws Exception {
        try {
            closeForeignTransactionIfExist();
        } catch (PersistenceException e) {
            throw e;
        } catch (Exception e) {
            throw wrapException(e);
        }

        DelegatingFramedGraph<?> graph = getContextExtension().getGraphCreateIfMissing();
        WrappedTransaction transaction = graph.tx();
        if (transaction.isOpen()) {
            throw new PersistenceException("Attempt to reopen transaction: " + transaction);
        }

        log.debug("Opening a new transaction {} on graph {}", transaction, graph);
        transaction.open();
    }

    @Override
    public void commit() throws Exception {
        commitOrRollback(true);
    }

    @Override
    public void rollback() throws Exception {
        commitOrRollback(false);
    }

    private void closeForeignTransactionIfExist() throws Exception {
        DelegatingFramedGraph<?> graph = getContextExtension().getGraphCreateIfMissing();
        WrappedTransaction currentTx = graph.tx();
        if (currentTx.isOpen()) {
            log.debug("Closing an existing underlying transaction {} on graph {}", currentTx, graph);
            commitOrRollback(currentTx, false);
        }
    }

    private void commitOrRollback(boolean isSuccess) throws Exception {
        commitOrRollback(getContextExtension().getGraphCreateIfMissing().tx(), isSuccess);
    }

    private void commitOrRollback(WrappedTransaction transaction, boolean isSuccess) throws Exception {
        String action = isSuccess ? "commit" : "rollback";

        if (! transaction.isOpen()) {
            throw new IllegalStateException(String.format(
                    "Attempt to %s not opened transaction (%s)", action, getImplementationType()));
        }

        log.debug("Performing {} to the transaction ({})", action, getImplementationType());
        try {
            if (isSuccess) {
                transaction.commit();
            } else {
                transaction.rollback();
            }
        } catch (Exception ex) {
            throw wrapException(ex);
        }
    }

    private FermaContextExtension getContextExtension() {
        PersistenceContext context = PersistenceContextManager.INSTANCE.getContextCreateIfMissing();
        return getImplementation().getContextExtension(context);
    }
}
