/* Copyright 2020 Telstra Open Source
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

package org.openkilda.persistence.inmemory;

import org.openkilda.config.provider.ConfigurationProvider;
import org.openkilda.config.provider.PropertiesBasedConfigurationProvider;
import org.openkilda.model.GroupId;
import org.openkilda.model.IpSocketAddress;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchStatus;
import org.openkilda.persistence.PersistenceImplementationType;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.tx.TransactionManager;

import org.junit.Before;
import org.junit.BeforeClass;
import org.mockito.Mockito;

import java.util.Properties;

public abstract class InMemoryGraphBasedTest {
    public static final SwitchId SWITCH_ID_1 = new SwitchId(1);
    public static final SwitchId SWITCH_ID_2 = new SwitchId(2);
    public static final SwitchId SWITCH_ID_3 = new SwitchId(3);
    public static final SwitchId SWITCH_ID_4 = new SwitchId(4);
    public static final PathId PATH_ID_1 = new PathId("path_1");
    public static final PathId PATH_ID_2 = new PathId("path_2");
    public static final PathId PATH_ID_3 = new PathId("path_3");
    public static final String HA_FLOW_ID_1 = "test_ha_flow_1";
    public static final String HA_FLOW_ID_2 = "test_ha_flow_2";
    public static final String SUB_FLOW_ID_1 = "test_sub_flow_1";
    public static final String SUB_FLOW_ID_2 = "test_sub_flow_2";
    public static final String SUB_FLOW_ID_3 = "test_sub_flow_3";
    public static final String SUB_FLOW_ID_4 = "test_sub_flow_4";
    public static final String DESCRIPTION_1 = "description_1";
    public static final String DESCRIPTION_2 = "description_2";
    public static final String DESCRIPTION_3 = "description_3";
    public static final PathId SUB_PATH_ID_1 = new PathId("sub_path_id_1");
    public static final PathId SUB_PATH_ID_2 = new PathId("sub_path_id_2");
    public static final PathId SUB_PATH_ID_3 = new PathId("sub_path_id_3");
    public static final String DIVERSITY_GROUP_1 = "diversity_group_1";
    public static final int PORT_1 = 1;
    public static final int PORT_2 = 2;
    public static final int PORT_3 = 3;
    public static final int PORT_4 = 4;
    public static final int VLAN_1 = 5;
    public static final int VLAN_2 = 6;
    public static final int VLAN_3 = 7;
    public static final int ZERO_INNER_VLAN = 0;
    public static final int INNER_VLAN_1 = 8;
    public static final int INNER_VLAN_2 = 9;
    public static final int INNER_VLAN_3 = 10;
    public static final MeterId METER_ID_1 = new MeterId(11);
    public static final MeterId METER_ID_2 = new MeterId(12);
    public static final MeterId METER_ID_3 = new MeterId(13);
    public static final MeterId METER_ID_4 = new MeterId(14);
    public static final GroupId GROUP_ID_1 = new GroupId(15);
    public static final GroupId GROUP_ID_2 = new GroupId(16);

    protected static ConfigurationProvider configurationProvider;
    protected static InMemoryGraphPersistenceManager inMemoryGraphPersistenceManager;
    protected static InMemoryGraphPersistenceImplementation inMemoryGraphPersistenceImplementation;
    protected static PersistenceManager persistenceManager;
    protected static TransactionManager transactionManager;
    protected static RepositoryFactory repositoryFactory;

    @BeforeClass
    public static void initPersistenceManager() {
        Properties properties = new Properties();
        properties.put("persistence.implementation.default", PersistenceImplementationType.IN_MEMORY_GRAPH.name());
        configurationProvider = new PropertiesBasedConfigurationProvider(properties);
        inMemoryGraphPersistenceImplementation = new InMemoryGraphPersistenceImplementation(configurationProvider);
        InMemoryGraphPersistenceImplementation persistenceImplementation = Mockito.spy(
                inMemoryGraphPersistenceImplementation);
        repositoryFactory = Mockito.spy(inMemoryGraphPersistenceImplementation.getRepositoryFactory());
        Mockito.when(persistenceImplementation.getRepositoryFactory()).thenReturn(repositoryFactory);

        inMemoryGraphPersistenceManager = new InMemoryGraphPersistenceManager(
                configurationProvider, PersistenceImplementationType.IN_MEMORY_GRAPH, persistenceImplementation);
        inMemoryGraphPersistenceManager.install();

        persistenceManager = Mockito.spy(inMemoryGraphPersistenceManager);
        transactionManager = inMemoryGraphPersistenceManager.getTransactionManager();
    }

    @Before
    public void cleanTinkerGraph() {
        inMemoryGraphPersistenceImplementation.purgeData();
    }

    protected Switch createTestSwitch(long switchId) {
        return createTestSwitch(new SwitchId(switchId));
    }

    protected Switch createTestSwitch(SwitchId switchId) {
        Switch sw = Switch.builder()
                .switchId(switchId)
                .description("test_description")
                .socketAddress(new IpSocketAddress("10.0.0.1", 30070))
                .controller("test_ctrl")
                .hostname("test_host_" + switchId)
                .status(SwitchStatus.ACTIVE)
                .build();
        repositoryFactory.createSwitchRepository().add(sw);
        return sw;
    }
}
