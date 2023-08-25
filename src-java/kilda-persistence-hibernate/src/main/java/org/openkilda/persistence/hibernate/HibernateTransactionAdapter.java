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

package org.openkilda.persistence.hibernate;

import org.openkilda.persistence.context.PersistenceContext;
import org.openkilda.persistence.context.PersistenceContextManager;
import org.openkilda.persistence.tx.ImplementationTransactionAdapter;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.context.internal.ManagedSessionContext;

@Slf4j
public class HibernateTransactionAdapter extends ImplementationTransactionAdapter<HibernatePersistenceImplementation> {
    private Session session;

    public HibernateTransactionAdapter(HibernatePersistenceImplementation implementation) {
        super(implementation);
    }

    @Override
    public void open() throws Exception {
        session = getSessionFactory().openSession();
        ManagedSessionContext.bind(session);

        log.trace("Open new hibernate transaction in thread {}", Thread.currentThread().getName());
        session.beginTransaction();
    }

    @Override
    public void commit() throws Exception {
        commitOrRollback(true);
    }

    @Override
    public void rollback() throws Exception {
        commitOrRollback(false);
    }

    private void commitOrRollback(boolean isSuccess) throws Exception {
        if (session == null) {
            throw new IllegalStateException("The session was not created");
        }

        Transaction transaction = session.getTransaction();
        log.debug(
                "Performing {} for hibernate transaction in thread {} ({})",
                isSuccess ? "commit" : "rollback", Thread.currentThread().getName(), transaction);
        try {
            if (isSuccess) {
                transaction.commit();
            } else {
                transaction.rollback();
            }
        } catch (Exception e) {
            throw wrapException(e);
        } finally {
            ManagedSessionContext.unbind(getSessionFactory());
            session.close();
        }
    }

    private SessionFactory getSessionFactory() {
        PersistenceContext context = PersistenceContextManager.INSTANCE.getContextCreateIfMissing();
        HibernateContextExtension contextExtension = getImplementation().getContextExtension(context);
        return contextExtension.getSessionFactoryCreateIfMissing();
    }
}
