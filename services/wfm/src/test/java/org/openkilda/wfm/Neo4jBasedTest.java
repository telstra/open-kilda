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

package org.openkilda.wfm;

import org.openkilda.config.provider.PropertiesBasedConfigurationProvider;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.impl.Neo4jSessionFactory;
import org.openkilda.persistence.spi.PersistenceProvider;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.rules.TemporaryFolder;

import java.util.Properties;

public abstract class Neo4jBasedTest {

    private static EmbeddedNeo4jDatabase embeddedNeo4jDb;
    protected static TransactionManager txManager;
    protected static PersistenceManager persistenceManager;
    protected static PropertiesBasedConfigurationProvider configurationProvider;

    @ClassRule
    public static TemporaryFolder fsData = new TemporaryFolder();

    @BeforeClass
    public static void startEmbeddedNeo4j() {
        embeddedNeo4jDb = new EmbeddedNeo4jDatabase(fsData.getRoot());

        Properties configProps = new Properties();
        configProps.setProperty("neo4j.uri", embeddedNeo4jDb.getConnectionUri());
        configurationProvider = new PropertiesBasedConfigurationProvider(configProps);

        persistenceManager =
                PersistenceProvider.getInstance().createPersistenceManager(configurationProvider);

        txManager = persistenceManager.getTransactionManager();
    }

    @AfterClass
    public static void stopEmbeddedNeo4j() {
        embeddedNeo4jDb.stop();
    }

    @After
    public void cleanUpNeo4j() {
        ((Neo4jSessionFactory) txManager).getSession().purgeDatabase();
    }
}
