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

package org.openkilda.wfm.topology.ping.bolt;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowPair;
import org.openkilda.model.PathId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.TransitVlan;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowPairRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;

import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;

public class FlowFetcherTest {

    public static final String FLOW_ID = "flow1";
    public static final SwitchId SWITCH_ID_1 = new SwitchId(1);
    public static final SwitchId SWITCH_ID_2 = new SwitchId(2);
    public static final PathId PATH_ID_1 = new PathId("path1");
    public static final PathId PATH_ID_2 = new PathId("path2");

    FlowPairRepository flowPairRepository;
    PersistenceManager persistenceManager;

    @Before
    public void init() {
        flowPairRepository = mock(FlowPairRepository.class);

        RepositoryFactory repositoryFactory = mock(RepositoryFactory.class);
        when(repositoryFactory.createFlowPairRepository()).thenReturn(flowPairRepository);

        persistenceManager = mock(PersistenceManager.class);
        when(persistenceManager.getRepositoryFactory()).thenReturn(repositoryFactory);
    }

    @Test
    public void refreshHeapWithInvalidFlowPairTest() {
        FlowPair invalidFlowPair = new FlowPair(
                Flow.builder()
                        .flowId(FLOW_ID) // flow has no paths
                        .srcSwitch(Switch.builder().switchId(SWITCH_ID_1).build())
                        .destSwitch(Switch.builder().switchId(SWITCH_ID_2).build())
                        .build(),
                new TransitVlan(FLOW_ID, PATH_ID_1, 0),
                new TransitVlan(FLOW_ID, PATH_ID_2, 0));

        when(flowPairRepository.findWithPeriodicPingsEnabled()).thenReturn(Lists.newArrayList(invalidFlowPair));
        FlowFetcher flowFetcher = new FlowFetcher(persistenceManager, 0);

        // init() method will initialise 'flowPairRepository' and call refreshHead()
        flowFetcher.init();
        // no exceptions means that refreshHead() method handled invalid flow
    }
}
