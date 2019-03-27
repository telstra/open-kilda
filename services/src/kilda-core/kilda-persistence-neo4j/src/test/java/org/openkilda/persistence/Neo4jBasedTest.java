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

package org.openkilda.persistence;

import org.openkilda.persistence.repositories.impl.Neo4jSessionFactory;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.neo4j.ogm.testutil.TestServer;

public abstract class Neo4jBasedTest {
    protected static TestServer testServer;
    protected static PersistenceManager persistenceManager;
    protected static Neo4jTransactionManager txManager;
    protected static Neo4jSessionFactory neo4jSessionFactory;

    @BeforeClass
    public static void runTestServer() {
        testServer = new TestServer(true, true, 5);

        persistenceManager = new Neo4jPersistenceManager(new Neo4jConfig() {
            @Override
            public String getUri() {
                return testServer.getUri();
            }

            @Override
            public String getLogin() {
                return testServer.getUsername();
            }

            @Override
            public String getPassword() {
                return testServer.getPassword();
            }

            @Override
            public int getConnectionPoolSize() {
                return 50;
            }

            @Override
            public String getIndexesAuto() {
                return "update";
            }
        });

        txManager = (Neo4jTransactionManager) persistenceManager.getTransactionManager();
        neo4jSessionFactory = txManager;
    }

    @AfterClass
    public static void shutdownTestServer() {
        testServer.shutdown();
    }

    @After
    public void cleanUpTestServer() {
        neo4jSessionFactory.getSession().purgeDatabase();
    }
}
