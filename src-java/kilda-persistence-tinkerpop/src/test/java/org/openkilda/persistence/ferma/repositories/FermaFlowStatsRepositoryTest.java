/* Copyright 2023 Telstra Open Source
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

package org.openkilda.persistence.ferma.repositories;


import static org.junit.jupiter.api.Assertions.assertEquals;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowStats;
import org.openkilda.model.HaSubFlow;
import org.openkilda.model.Switch;
import org.openkilda.persistence.inmemory.InMemoryGraphBasedTest;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.FlowStatsRepository;
import org.openkilda.persistence.repositories.HaSubFlowRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class FermaFlowStatsRepositoryTest extends InMemoryGraphBasedTest {
    static final String TEST_FLOW_ID = "test_flow_1";
    private static final Long FORWARD_LATENCY = 5000L;
    private static final Long REVERSE_LATENCY = 10000L;

    private FlowRepository flowRepository;
    private HaSubFlowRepository haSubFlowRepository;
    private FlowStatsRepository flowStatsRepository;

    private Switch switchA;
    private Switch switchB;

    @BeforeEach
    public void setUp() {
        flowRepository = repositoryFactory.createFlowRepository();
        haSubFlowRepository = repositoryFactory.createHaSubFlowRepository();
        flowStatsRepository = repositoryFactory.createFlowStatsRepository();

        switchA = createTestSwitch(SWITCH_ID_1);
        switchB = createTestSwitch(SWITCH_ID_2);
    }

    @Test
    public void removeFlowStatByFlowId() {
        FlowStats flowStats = FlowStats.builder()
                .flowObj(createFlow())
                .forwardLatency(FORWARD_LATENCY)
                .reverseLatency(REVERSE_LATENCY)
                .build();

        flowStatsRepository.add(flowStats);
        assertEquals(flowStatsRepository.findAll().size(), 1);

        flowStatsRepository.removeByFlowId(TEST_FLOW_ID);
        assertEquals(flowStatsRepository.findAll().size(), 0);
    }

    @Test
    public void removeFlowStatByHaSubFlowId() {
        FlowStats flowStats = new FlowStats(createHaSubFlow(), FORWARD_LATENCY, REVERSE_LATENCY);

        flowStatsRepository.add(flowStats);
        assertEquals(flowStatsRepository.findAll().size(), 1);

        flowStatsRepository.removeByFlowId(SUB_FLOW_ID_1);
        assertEquals(flowStatsRepository.findAll().size(), 0);
    }

    private Flow createFlow() {
        Flow flow = Flow.builder()
                .flowId(TEST_FLOW_ID)
                .srcSwitch(switchA)
                .destSwitch(switchB)
                .build();
        flowRepository.add(flow);
        return flow;
    }

    private HaSubFlow createHaSubFlow() {
        HaSubFlow haSubFlow = HaSubFlow.builder()
                .haSubFlowId(SUB_FLOW_ID_1)
                .endpointSwitch(switchA)
                .build();
        haSubFlowRepository.add(haSubFlow);
        return haSubFlow;
    }
}
