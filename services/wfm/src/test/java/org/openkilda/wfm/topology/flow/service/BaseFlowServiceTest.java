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

import static org.junit.Assert.assertEquals;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchStatus;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.Neo4jBasedTest;

import org.junit.Test;

public class BaseFlowServiceTest extends Neo4jBasedTest {
    private static final SwitchId SWITCH_ID_1 = new SwitchId("00:00:00:00:00:00:00:01");
    private static final SwitchId SWITCH_ID_2 = new SwitchId("00:00:00:00:00:00:00:02");

    @Test
    public void shouldUpdateFlowStatus() {
        BaseFlowService flowService = new BaseFlowService(persistenceManager);
        FlowRepository flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();

        Flow flow = Flow.builder()
                .flowId("test-flow")
                .srcSwitch(getOrCreateSwitch(SWITCH_ID_1))
                .srcPort(1)
                .srcVlan(101)
                .destSwitch(getOrCreateSwitch(SWITCH_ID_2))
                .destPort(2)
                .destVlan(102)
                .bandwidth(0)
                .status(FlowStatus.IN_PROGRESS)
                .build();
        flowRepository.createOrUpdate(flow);

        flowService.updateFlowStatus(flow.getFlowId(), FlowStatus.UP);

        Flow foundFlow = flowRepository.findById(flow.getFlowId()).iterator().next();
        assertEquals(FlowStatus.UP, foundFlow.getStatus());
    }

    private Switch getOrCreateSwitch(SwitchId switchId) {
        SwitchRepository switchRepository = persistenceManager.getRepositoryFactory().createSwitchRepository();
        return switchRepository.findById(switchId).orElseGet(() -> {
            Switch sw = Switch.builder().switchId(switchId).status(SwitchStatus.ACTIVE).build();
            switchRepository.createOrUpdate(sw);
            return sw;
        });
    }
}
