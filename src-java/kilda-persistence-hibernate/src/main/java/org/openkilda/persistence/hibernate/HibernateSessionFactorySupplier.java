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

import org.openkilda.persistence.hibernate.entities.EntityBase;
import org.openkilda.persistence.hibernate.entities.history.HibernateFlowEvent;
import org.openkilda.persistence.hibernate.entities.history.HibernateFlowEventAction;
import org.openkilda.persistence.hibernate.entities.history.HibernateFlowEventDump;
import org.openkilda.persistence.hibernate.entities.history.HibernatePortEvent;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.SessionFactory;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataBuilder;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.SessionFactoryBuilder;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.internal.ManagedSessionContext;
import org.hibernate.dialect.MySQLDialect;

import java.io.Serializable;
import java.util.function.Supplier;

@Slf4j
public class HibernateSessionFactorySupplier implements Supplier<SessionFactory>, Serializable {
    private final HibernateConfig hibernateConfig;

    private transient volatile SessionFactory sessionFactory;

    public HibernateSessionFactorySupplier(HibernateConfig hibernateConfig) {
        this.hibernateConfig = hibernateConfig;
    }

    @Override
    public SessionFactory get() {
        if (sessionFactory == null) {
            synchronized (this) {
                if (sessionFactory == null) {
                    sessionFactory = makeHibernateSessionFactory();
                }
            }
        }
        return sessionFactory;
    }

    private SessionFactory makeHibernateSessionFactory() {
        StandardServiceRegistry standardRegistry = new StandardServiceRegistryBuilder()
                .applySetting(AvailableSettings.DRIVER, hibernateConfig.getDriverClass())
                .applySetting(AvailableSettings.USER, hibernateConfig.getUser())
                .applySetting(AvailableSettings.PASS, hibernateConfig.getPassword())
                .applySetting(AvailableSettings.URL, hibernateConfig.getUrl())
                .applySetting(AvailableSettings.DIALECT, MySQLDialect.class.getName())
                .applySetting(AvailableSettings.CURRENT_SESSION_CONTEXT_CLASS, ManagedSessionContext.class.getName())
                .applySetting(AvailableSettings.C3P0_IDLE_TEST_PERIOD, 600)  // seconds?
                .applySetting(AvailableSettings.C3P0_CONFIG_PREFIX + ".testConnectionOnCheckout", true)
                .applySetting(AvailableSettings.C3P0_CONFIG_PREFIX + ".preferredTestQuery", "SELECT 1")
                // TODO(surabujin): detect debugging mode and enable for it
                .applySetting(AvailableSettings.SHOW_SQL, false)
                .applySetting(AvailableSettings.FORMAT_SQL, true)
                .applySetting(AvailableSettings.USE_SQL_COMMENTS, true)
                .build();

        MetadataSources sources = new MetadataSources(standardRegistry);
        // TODO(surabujin): find solution to automatically scan @Entity annotations
        sources.addAnnotatedClass(EntityBase.class);
        sources.addAnnotatedClass(HibernateFlowEvent.class);
        sources.addAnnotatedClass(HibernateFlowEventAction.class);
        sources.addAnnotatedClass(HibernateFlowEventDump.class);
        sources.addAnnotatedClass(HibernatePortEvent.class);

        MetadataBuilder metadataBuilder = sources.getMetadataBuilder();
        Metadata metadata = metadataBuilder.build();

        SessionFactoryBuilder sessionFactoryBuilder = metadata.getSessionFactoryBuilder();

        return sessionFactoryBuilder.build();
    }
}
