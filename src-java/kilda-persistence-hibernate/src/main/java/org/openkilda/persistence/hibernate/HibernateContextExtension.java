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

import org.openkilda.persistence.PersistenceImplementationType;
import org.openkilda.persistence.context.PersistentContextExtension;

import lombok.Getter;
import org.hibernate.SessionFactory;

public class HibernateContextExtension implements PersistentContextExtension {
    @Getter
    private final PersistenceImplementationType implementationType;

    private final HibernateSessionFactorySupplier sessionFactorySupplier;

    public HibernateContextExtension(
            PersistenceImplementationType implementationType, HibernateSessionFactorySupplier sessionFactorySupplier) {
        this.implementationType = implementationType;
        this.sessionFactorySupplier = sessionFactorySupplier;
    }

    public SessionFactory getSessionFactoryCreateIfMissing() {
        return sessionFactorySupplier.get();
    }
}
