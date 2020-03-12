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

import org.openkilda.config.provider.PropertiesBasedConfigurationProvider;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchStatus;
import org.openkilda.persistence.dummy.PersistenceDummyEntityFactory;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.impl.Neo4jSessionFactory;
import org.openkilda.persistence.spi.PersistenceProvider;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.Collections;
import java.util.Properties;

public abstract class Neo4jBasedTest {

    protected static PersistenceManager persistenceManager;
    protected static RepositoryFactory repositoryFactorySpy;

    protected static PersistenceDummyEntityFactory dummyFactory;

    private static EmbeddedNeo4jDatabase embeddedNeo4jDb;
    protected static Neo4jTransactionManager txManager;
    protected static Neo4jSessionFactory neo4jSessionFactory;
    protected static PropertiesBasedConfigurationProvider configurationProvider;

    @ClassRule
    public static TemporaryFolder fsData = new TemporaryFolder();

    @BeforeClass
    public static void startEmbeddedNeo4j() {
        embeddedNeo4jDb = new EmbeddedNeo4jDatabase(fsData.getRoot());

        Properties configProps = new Properties();
        configProps.setProperty("neo4j.uri", embeddedNeo4jDb.getConnectionUri());
        configProps.setProperty("neo4j.indexes.auto", "update"); // ask to create indexes/constraints if needed
        configurationProvider = new PropertiesBasedConfigurationProvider(configProps);

        PersistenceManager realPersistenceManager = PersistenceProvider.getInstance()
                .createPersistenceManager(configurationProvider);
        persistenceManager = Mockito.spy(realPersistenceManager);

        repositoryFactorySpy = Mockito.spy(realPersistenceManager.getRepositoryFactory());
        Mockito.when(persistenceManager.getRepositoryFactory()).thenReturn(repositoryFactorySpy);

        dummyFactory = new PersistenceDummyEntityFactory(persistenceManager);

        txManager = (Neo4jTransactionManager) persistenceManager.getTransactionManager();
        neo4jSessionFactory = txManager;
    }

    @AfterClass
    public static void stopEmbeddedNeo4j() {
        if (embeddedNeo4jDb != null) {
            embeddedNeo4jDb.stop();
        }
    }

    @After
    public void cleanUpNeo4j() {
        neo4jSessionFactory.getSession().purgeDatabase();
    }

    protected Switch buildTestSwitch(long switchId) {
        try {
            return Switch.builder()
                    .switchId(new SwitchId(switchId))
                    .socketAddress(new InetSocketAddress(InetAddress.getByName("10.0.0.1"), 30070))
                    .controller("test_ctrl")
                    .description("test_description")
                    .hostname("test_host_" + switchId)
                    .status(SwitchStatus.ACTIVE)
                    .timeCreate(Instant.now())
                    .timeModify(Instant.now())
                    .features(Collections.emptySet())
                    .build();
        } catch (UnknownHostException e) {
            throw new IllegalStateException(e);
        }
    }
}
