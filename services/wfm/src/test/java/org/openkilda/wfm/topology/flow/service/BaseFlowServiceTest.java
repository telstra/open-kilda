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

import org.openkilda.model.FlowPair;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchStatus;
import org.openkilda.persistence.repositories.FlowPairRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.Neo4jBasedTest;

import org.junit.Test;

import java.util.Optional;

public class BaseFlowServiceTest extends Neo4jBasedTest {
    private static final SwitchId SWITCH_ID_1 = new SwitchId("00:00:00:00:00:00:00:01");
    private static final SwitchId SWITCH_ID_2 = new SwitchId("00:00:00:00:00:00:00:02");

    @Test
    public void shouldUpdateFlowStatus() {
        BaseFlowService flowService = new BaseFlowService(persistenceManager);
        FlowPairRepository flowPairRepository = persistenceManager.getRepositoryFactory().createFlowPairRepository();

        String flowId = "test-flow";
        FlowPair flowPair = new FlowPair(flowId,
                getOrCreateSwitch(SWITCH_ID_1), 1, 101,
                getOrCreateSwitch(SWITCH_ID_2), 2, 102);
        flowPair.getForward().setBandwidth(0);
        flowPair.setStatus(FlowStatus.IN_PROGRESS);

        flowPairRepository.createOrUpdate(flowPair);

        flowService.updateFlowStatus(flowId, FlowStatus.UP);

        Optional<FlowPair> foundFlow = flowPairRepository.findById(flowId);
        assertEquals(FlowStatus.UP, foundFlow.get().getForward().getStatus());
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
