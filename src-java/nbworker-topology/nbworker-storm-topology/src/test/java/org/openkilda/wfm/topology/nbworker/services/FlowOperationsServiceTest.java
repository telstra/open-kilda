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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.openkilda.messaging.model.FlowDto;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchStatus;
import org.openkilda.persistence.Neo4jBasedTest;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.error.FlowNotFoundException;
import org.openkilda.wfm.share.flow.TestFlowBuilder;
import org.openkilda.wfm.topology.nbworker.services.FlowOperationsService.UpdateFlowResult;

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
        Long maxLatency = 555L;
        Integer priority = 777;

        Switch switchA = new Switch();
        switchA.setSwitchId(new SwitchId(1));
        switchA.setStatus(SwitchStatus.ACTIVE);
        switchRepository.createOrUpdate(switchA);

        Switch switchB = new Switch();
        switchB.setSwitchId(new SwitchId(2));
        switchB.setStatus(SwitchStatus.ACTIVE);
        switchRepository.createOrUpdate(switchB);

        Flow flow = new TestFlowBuilder()
                .flowId(testFlowId)
                .srcSwitch(switchA)
                .srcPort(1)
                .srcVlan(10)
                .destSwitch(switchB)
                .destPort(2)
                .destVlan(11)
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .build();
        flow.setStatus(FlowStatus.UP);
        flowRepository.createOrUpdate(flow);

        FlowDto receivedFlow = FlowDto.builder()
                .flowId(testFlowId)
                .maxLatency(maxLatency)
                .priority(priority)
                .build();

        Flow updatedFlow = flowOperationsService.updateFlow(null, receivedFlow);

        assertEquals(maxLatency, updatedFlow.getMaxLatency());
        assertEquals(priority, updatedFlow.getPriority());

        receivedFlow = FlowDto.builder()
                .flowId(testFlowId)
                .build();
        updatedFlow = flowOperationsService.updateFlow(null, receivedFlow);

        assertEquals(maxLatency, updatedFlow.getMaxLatency());
        assertEquals(priority, updatedFlow.getPriority());
    }

    @Test
    public void shouldPrepareFlowUpdateResultWithChangedStrategyReason() {
        // given: FlowDto with COST strategy and Flow with MAX_LATENCY strategy
        String flowId = "test_flow_id";
        FlowDto flowDto = FlowDto.builder()
                .flowId(flowId)
                .maxLatency(100L)
                .pathComputationStrategy(PathComputationStrategy.COST)
                .build();
        Flow flow = Flow.builder()
                .flowId(flowId)
                .srcSwitch(Switch.builder().switchId(new SwitchId(1)).build())
                .destSwitch(Switch.builder().switchId(new SwitchId(2)).build())
                .pathComputationStrategy(PathComputationStrategy.MAX_LATENCY)
                .build();

        // when: compare this flows
        UpdateFlowResult result = flowOperationsService.prepareFlowUpdateResult(flowDto, flow).build();

        // then: needRerouteFlow flag set to true and rerouteReason is "path computation strategy was changed"
        assertTrue(result.isNeedRerouteFlow());
        assertTrue(result.getRerouteReason().contains("path computation strategy was changed"));
    }

    @Test
    public void shouldPrepareFlowUpdateResultWithChangedMaxLatencyReasonFirstCase() {
        // given: FlowDto with max latency and no strategy and Flow with MAX_LATENCY strategy and no max latency
        String flowId = "test_flow_id";
        FlowDto flowDto = FlowDto.builder()
                .flowId(flowId)
                .maxLatency(100L)
                .build();
        Flow flow = Flow.builder()
                .flowId(flowId)
                .srcSwitch(Switch.builder().switchId(new SwitchId(1)).build())
                .destSwitch(Switch.builder().switchId(new SwitchId(2)).build())
                .pathComputationStrategy(PathComputationStrategy.MAX_LATENCY)
                .build();

        // when: compare this flows
        UpdateFlowResult result = flowOperationsService.prepareFlowUpdateResult(flowDto, flow).build();

        // then: needRerouteFlow flag set to true and rerouteReason is "max latency was changed"
        assertTrue(result.isNeedRerouteFlow());
        assertTrue(result.getRerouteReason().contains("max latency was changed"));
    }

    @Test
    public void shouldPrepareFlowUpdateResultWithChangedMaxLatencyReasonSecondCase() {
        // given: FlowDto with max latency and MAX_LATENCY strategy
        //        and Flow with MAX_LATENCY strategy and no max latency
        String flowId = "test_flow_id";
        FlowDto flowDto = FlowDto.builder()
                .flowId(flowId)
                .maxLatency(100L)
                .pathComputationStrategy(PathComputationStrategy.MAX_LATENCY)
                .build();
        Flow flow = Flow.builder()
                .flowId(flowId)
                .srcSwitch(Switch.builder().switchId(new SwitchId(1)).build())
                .destSwitch(Switch.builder().switchId(new SwitchId(2)).build())
                .pathComputationStrategy(PathComputationStrategy.MAX_LATENCY)
                .build();

        // when: compare this flows
        UpdateFlowResult result = flowOperationsService.prepareFlowUpdateResult(flowDto, flow).build();

        // then: needRerouteFlow flag set to true and rerouteReason is "max latency was changed"
        assertTrue(result.isNeedRerouteFlow());
        assertTrue(result.getRerouteReason().contains("max latency was changed"));
    }

    @Test
    public void shouldPrepareFlowUpdateResultShouldNotRerouteFirstCase() {
        // given: FlowDto with max latency and MAX_LATENCY strategy
        //        and Flow with MAX_LATENCY strategy and same max latency
        String flowId = "test_flow_id";
        FlowDto flowDto = FlowDto.builder()
                .flowId(flowId)
                .maxLatency(100L)
                .pathComputationStrategy(PathComputationStrategy.MAX_LATENCY)
                .build();
        Flow flow = Flow.builder()
                .flowId(flowId)
                .srcSwitch(Switch.builder().switchId(new SwitchId(1)).build())
                .destSwitch(Switch.builder().switchId(new SwitchId(2)).build())
                .maxLatency(100L)
                .pathComputationStrategy(PathComputationStrategy.MAX_LATENCY)
                .build();

        // when: compare this flows
        UpdateFlowResult result = flowOperationsService.prepareFlowUpdateResult(flowDto, flow).build();

        // then: needRerouteFlow flag set to false and no rerouteReason
        assertFalse(result.isNeedRerouteFlow());
        assertNull(result.getRerouteReason());
    }

    @Test
    public void shouldPrepareFlowUpdateResultShouldNotRerouteSecondCase() {
        // given: FlowDto with no max latency and no strategy
        //        and Flow with MAX_LATENCY strategy and max latency
        String flowId = "test_flow_id";
        FlowDto flowDto = FlowDto.builder()
                .flowId(flowId)
                .build();
        Flow flow = Flow.builder()
                .flowId(flowId)
                .srcSwitch(Switch.builder().switchId(new SwitchId(1)).build())
                .destSwitch(Switch.builder().switchId(new SwitchId(2)).build())
                .maxLatency(100L)
                .pathComputationStrategy(PathComputationStrategy.MAX_LATENCY)
                .build();

        UpdateFlowResult result = flowOperationsService.prepareFlowUpdateResult(flowDto, flow).build();

        assertFalse(result.isNeedRerouteFlow());
        assertNull(result.getRerouteReason());
    }
}
