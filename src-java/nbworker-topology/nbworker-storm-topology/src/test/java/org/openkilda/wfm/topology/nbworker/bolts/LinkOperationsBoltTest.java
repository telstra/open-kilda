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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

import org.openkilda.messaging.model.LinkPropsDto;
import org.openkilda.messaging.model.NetworkEndpoint;
import org.openkilda.messaging.nbtopology.request.LinkPropsPut;
import org.openkilda.messaging.nbtopology.response.LinkPropsResponse;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.inmemory.InMemoryGraphPersistenceManager;
import org.openkilda.persistence.repositories.SwitchRepository;

import org.apache.storm.task.TopologyContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

@ExtendWith(MockitoExtension.class)
public class LinkOperationsBoltTest {
    private static final SwitchId SWITCH_ID_1 = new SwitchId("00:00:00:00:00:00:00:01");
    private static final SwitchId SWITCH_ID_2 = new SwitchId("00:00:00:00:00:00:00:02");

    private static InMemoryGraphPersistenceManager persistenceManager;

    @Mock
    private TopologyContext topologyContext;

    @BeforeAll
    public static void initPersistenceManager() {
        persistenceManager = InMemoryGraphPersistenceManager.newInstance();
        persistenceManager.install();
    }

    @BeforeEach
    public void setUp() throws Exception {
        persistenceManager.getInMemoryImplementation().purgeData();

        when(topologyContext.getThisTaskId()).thenReturn(1);
    }

    @Test
    public void shouldCreateLinkProps() {
        SwitchRepository switchRepository = persistenceManager.getRepositoryFactory().createSwitchRepository();

        switchRepository.add(Switch.builder().switchId(SWITCH_ID_1).build());
        switchRepository.add(Switch.builder().switchId(SWITCH_ID_2).build());

        LinkOperationsBolt bolt = new LinkOperationsBolt(persistenceManager);
        bolt.prepare(null, topologyContext, null);
        LinkPropsPut linkPropsPutRequest = new LinkPropsPut(new LinkPropsDto(
                new NetworkEndpoint(SWITCH_ID_1, 1),
                new NetworkEndpoint(SWITCH_ID_2, 1),
                Collections.emptyMap()));

        LinkPropsResponse response = (LinkPropsResponse) bolt.processRequest(null, linkPropsPutRequest).get(0);
        assertNotNull(response.getLinkProps());
    }
}
