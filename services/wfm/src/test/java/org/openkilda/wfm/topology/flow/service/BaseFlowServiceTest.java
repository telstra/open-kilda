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

package org.openkilda.wfm.topology.flow.service;

import static org.junit.Assert.assertTrue;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowPair;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchStatus;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.Neo4jBasedTest;
import org.openkilda.wfm.share.flow.TestFlowBuilder;

import org.junit.Before;
import org.junit.Test;

import java.util.Optional;

public class BaseFlowServiceTest extends Neo4jBasedTest {
    private static final SwitchId SWITCH_ID_1 = new SwitchId("00:00:00:00:00:00:00:01");
    private static final SwitchId SWITCH_ID_2 = new SwitchId("00:00:00:00:00:00:00:02");

    private SwitchRepository switchRepository;
    private FlowRepository flowRepository;
    private BaseFlowService flowService;

    @Before
    public void setUp() {
        switchRepository = persistenceManager.getRepositoryFactory().createSwitchRepository();
        flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();
        flowService = new BaseFlowService(persistenceManager);
    }

    @Test
    public void shouldFindFlowPair() {
        Flow flow = new TestFlowBuilder()
                .srcSwitch(getOrCreateSwitch(SWITCH_ID_1))
                .destSwitch(getOrCreateSwitch(SWITCH_ID_2))
                .build();
        flowRepository.createOrUpdate(flow);

        Optional<FlowPair> foundFlowPair = flowService.getFlowPair(flow.getFlowId());

        assertTrue(foundFlowPair.isPresent());
    }

    private Switch getOrCreateSwitch(SwitchId switchId) {
        return switchRepository.findById(switchId).orElseGet(() -> {
            Switch sw = Switch.builder().switchId(switchId).status(SwitchStatus.ACTIVE).build();
            switchRepository.createOrUpdate(sw);
            return sw;
        });
    }
}
