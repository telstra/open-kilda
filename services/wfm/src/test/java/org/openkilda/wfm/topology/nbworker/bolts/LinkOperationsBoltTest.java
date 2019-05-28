/* Copyright 2019 Telstra Open Source
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

package org.openkilda.wfm.topology.nbworker.bolts;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.when;

import org.openkilda.config.provider.PropertiesBasedConfigurationProvider;
import org.openkilda.messaging.model.LinkPropsDto;
import org.openkilda.messaging.model.NetworkEndpoint;
import org.openkilda.messaging.nbtopology.request.LinkPropsPut;
import org.openkilda.messaging.nbtopology.response.LinkPropsResponse;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.Neo4jConfig;
import org.openkilda.persistence.Neo4jPersistenceManager;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.EmbeddedNeo4jDatabase;

import org.apache.storm.task.TopologyContext;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.Properties;

@RunWith(MockitoJUnitRunner.class)
public class LinkOperationsBoltTest {
    private static final SwitchId SWITCH_ID_1 = new SwitchId("00:00:00:00:00:00:00:01");
    private static final SwitchId SWITCH_ID_2 = new SwitchId("00:00:00:00:00:00:00:02");
    private static final int ISL_COST_WHEN_UNDER_MAINTENANCE = 10000;

    @ClassRule
    public static TemporaryFolder fsData = new TemporaryFolder();

    private static EmbeddedNeo4jDatabase embeddedNeo4jDb;
    private static PersistenceManager persistenceManager;

    @Mock
    private TopologyContext topologyContext;

    @BeforeClass
    public static void setupOnce() {
        embeddedNeo4jDb = new EmbeddedNeo4jDatabase(fsData.getRoot());

        Properties configProps = new Properties();
        configProps.setProperty("neo4j.uri", embeddedNeo4jDb.getConnectionUri());
        configProps.setProperty("neo4j.indexes.auto", "update"); // ask to create indexes/constraints if needed
        PropertiesBasedConfigurationProvider configurationProvider =
                new PropertiesBasedConfigurationProvider(configProps);
        persistenceManager = new Neo4jPersistenceManager(configurationProvider.getConfiguration(Neo4jConfig.class));
    }

    @Before
    public void setUp() throws Exception {
        when(topologyContext.getThisTaskId()).thenReturn(1);
    }

    @AfterClass
    public static void teardownOnce() {
        embeddedNeo4jDb.stop();
    }

    @Test
    public void shouldCreateLinkProps() {
        SwitchRepository switchRepository = persistenceManager.getRepositoryFactory().createSwitchRepository();

        switchRepository.createOrUpdate(Switch.builder().switchId(SWITCH_ID_1).build());
        switchRepository.createOrUpdate(Switch.builder().switchId(SWITCH_ID_2).build());

        LinkOperationsBolt bolt = new LinkOperationsBolt(persistenceManager, ISL_COST_WHEN_UNDER_MAINTENANCE);
        bolt.prepare(null, topologyContext, null);
        LinkPropsPut linkPropsPutRequest = new LinkPropsPut(new LinkPropsDto(
                new NetworkEndpoint(SWITCH_ID_1, 1),
                new NetworkEndpoint(SWITCH_ID_2, 1),
                Collections.emptyMap()));

        LinkPropsResponse response = (LinkPropsResponse) bolt.processRequest(null, linkPropsPutRequest).get(0);
        assertNotNull(response.getLinkProps());
    }
}
