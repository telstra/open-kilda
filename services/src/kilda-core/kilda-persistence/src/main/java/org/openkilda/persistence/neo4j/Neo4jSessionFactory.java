/* Copyright 2018 Telstra Open Source
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

package org.openkilda.persistence.neo4j;

import org.neo4j.ogm.config.ClasspathConfigurationSource;
import org.neo4j.ogm.config.Configuration;
import org.neo4j.ogm.session.Session;
import org.neo4j.ogm.session.SessionFactory;

/**
 * Used to create {@link Session} instances for interacting with Neo4J OGM.
 */
public final class Neo4jSessionFactory {
    public static final Neo4jSessionFactory INSTANCE = new Neo4jSessionFactory();

    private final SessionFactory sessionFactory;

    private Neo4jSessionFactory() {
        ClasspathConfigurationSource configurationSource = new ClasspathConfigurationSource("neo4j.properties");
        Configuration configuration = new Configuration.Builder(configurationSource).build();
        sessionFactory = new SessionFactory(configuration, "org.openkilda.model");
    }

    /**
     * Create a new session.
     *
     * @return the newly created session.
     */
    Session openSession() {
        return sessionFactory.openSession();
    }

    /**
     * Get the existing session if there's an active transaction, otherwise create a new session.
     *
     * @return the session.
     */
    public Session getSession() {
        Session session = Neo4jSessionHolder.INSTANCE.getSession();
        if (session != null) {
            return session;
        }
        return sessionFactory.openSession();
    }
}
