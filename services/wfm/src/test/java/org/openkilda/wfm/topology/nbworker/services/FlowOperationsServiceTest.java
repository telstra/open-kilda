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

package org.openkilda.wfm.topology.nbworker.services;

import static org.junit.Assert.assertEquals;

import org.openkilda.messaging.model.FlowDto;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchStatus;
import org.openkilda.model.UnidirectionalFlow;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.Neo4jBasedTest;
import org.openkilda.wfm.error.FlowNotFoundException;
import org.openkilda.wfm.share.flow.TestFlowBuilder;

import org.junit.BeforeClass;
import org.junit.Test;

public class FlowOperationsServiceTest extends Neo4jBasedTest {
    private static FlowOperationsService flowOperationsService;
    private static FlowRepository flowRepository;
    private static SwitchRepository switchRepository;

    @BeforeClass
    public static void setUpOnce() {
        flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();
        switchRepository = persistenceManager.getRepositoryFactory().createSwitchRepository();
        flowOperationsService = new FlowOperationsService(persistenceManager.getRepositoryFactory(),
                persistenceManager.getTransactionManager());
    }

    @Test
    public void shouldUpdateMaxLatencyAndPriorityFlowFields() throws FlowNotFoundException {
        String testFlowId = "flow_id";
        Integer maxLatency = 555;
        Integer priority = 777;

        Switch switchA = new Switch();
        switchA.setSwitchId(new SwitchId(1));
        switchA.setStatus(SwitchStatus.ACTIVE);
        switchRepository.createOrUpdate(switchA);

        Switch switchB = new Switch();
        switchB.setSwitchId(new SwitchId(2));
        switchB.setStatus(SwitchStatus.ACTIVE);
        switchRepository.createOrUpdate(switchB);

        UnidirectionalFlow flow = new TestFlowBuilder()
                .flowId(testFlowId)
                .srcSwitch(switchA)
                .srcPort(1)
                .srcVlan(10)
                .destSwitch(switchB)
                .destPort(2)
                .destVlan(11)
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .buildUnidirectionalFlow();
        flow.setStatus(FlowStatus.UP);
        flowRepository.createOrUpdate(flow.getFlow());

        FlowDto receivedFlow = FlowDto.builder()
                .flowId(testFlowId)
                .maxLatency(maxLatency)
                .priority(priority)
                .build();

        UnidirectionalFlow updatedFlow = flowOperationsService.updateFlow(receivedFlow);

        assertEquals(maxLatency, updatedFlow.getMaxLatency());
        assertEquals(priority, updatedFlow.getPriority());

        receivedFlow = FlowDto.builder()
                .flowId(testFlowId)
                .build();
        updatedFlow = flowOperationsService.updateFlow(receivedFlow);

        assertEquals(maxLatency, updatedFlow.getMaxLatency());
        assertEquals(priority, updatedFlow.getPriority());
    }
}
