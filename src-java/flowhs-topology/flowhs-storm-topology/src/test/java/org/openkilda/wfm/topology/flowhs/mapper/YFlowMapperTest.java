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

package org.openkilda.wfm.topology.flowhs.mapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.openkilda.messaging.command.yflow.YFlowDto;
import org.openkilda.model.Flow;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.YFlow;
import org.openkilda.model.YFlow.SharedEndpoint;
import org.openkilda.model.YSubFlow;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.HaFlowRepository;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public class YFlowMapperTest {
    private static final String SUB_FLOW_ID_1 = "subflow_1";
    private static final String SUB_FLOW_ID_2 = "subflow_2";
    private static final String HA_FLOW_ID_1 = "ha_flow_1";
    private static final String HA_FLOW_ID_2 = "ha_flow_2";
    private static final String FLOW_ID_1 = "flow_1";
    private static final String FLOW_ID_2 = "flow_2";
    private static final String Y_FLOW_ID_1 = "y_flow_1";
    private static final String Y_FLOW_ID_2 = "y_flow_2";
    private static final String GROUP_1 = "group_1";
    private static final SwitchId SWITCH_ID_1 = new SwitchId(1);
    private static final Switch SWITCH_1 = Switch.builder().switchId(SWITCH_ID_1).build();

    YFlowMapper mapper = YFlowMapper.INSTANCE;

    @Mock
    FlowRepository flowRepository;

    @Mock
    HaFlowRepository haFlowRepository;

    @Before
    public void init() {
        flowRepository = mock(FlowRepository.class);
        haFlowRepository = mock(HaFlowRepository.class);
    }

    @Test
    public void toHaFlowDtoWithDiversityTest() {
        YFlow yFlow = YFlow.builder()
                .yFlowId(Y_FLOW_ID_1)
                .sharedEndpoint(new SharedEndpoint(SWITCH_ID_1, 0))
                .build();
        Flow subFlow = buildYSubFlow(SUB_FLOW_ID_1, Y_FLOW_ID_1);
        YSubFlow ySubFlow = YSubFlow.builder().yFlow(yFlow).flow(subFlow).endpointSwitchId(SWITCH_ID_1).build();
        yFlow.addSubFlow(ySubFlow);

        when(flowRepository.findByDiverseGroupId(anyString()))
                .thenReturn(Lists.newArrayList(
                        buildFlow(FLOW_ID_1), buildFlow(FLOW_ID_2),
                        subFlow, buildYSubFlow(SUB_FLOW_ID_2, Y_FLOW_ID_2)));
        when(haFlowRepository.findHaFlowIdsByDiverseGroupId(anyString()))
                .thenReturn(Lists.newArrayList(HA_FLOW_ID_1, HA_FLOW_ID_2));

        YFlowDto result = mapper.toYFlowDto(yFlow, flowRepository, haFlowRepository);
        assertEquals(Y_FLOW_ID_1, result.getYFlowId());
        assertEquals(Sets.newHashSet(FLOW_ID_1, FLOW_ID_2), result.getDiverseWithFlows());
        assertEquals(Sets.newHashSet(Y_FLOW_ID_2), result.getDiverseWithYFlows());
        assertEquals(Sets.newHashSet(HA_FLOW_ID_1, HA_FLOW_ID_2), result.getDiverseWithHaFlows());
    }

    @Test
    public void toHaFlowDtoWithoutDiversityTest() {
        YFlow yFlow = YFlow.builder()
                .yFlowId(Y_FLOW_ID_1)
                .sharedEndpoint(new SharedEndpoint(SWITCH_ID_1, 0))
                .build();

        YFlowDto result = mapper.toYFlowDto(yFlow, flowRepository, haFlowRepository);
        assertEquals(Y_FLOW_ID_1, result.getYFlowId());
        assertTrue(result.getDiverseWithFlows().isEmpty());
        assertTrue(result.getDiverseWithYFlows().isEmpty());
        assertTrue(result.getDiverseWithHaFlows().isEmpty());
    }

    private static Flow buildFlow(String flowId) {
        return Flow.builder().flowId(flowId).srcSwitch(SWITCH_1).destSwitch(SWITCH_1).diverseGroupId(GROUP_1).build();
    }

    private static Flow buildYSubFlow(String flowId, String yFlowId) {
        return Flow.builder().flowId(flowId).yFlowId(yFlowId).srcSwitch(SWITCH_1).destSwitch(SWITCH_1)
                .diverseGroupId(GROUP_1).affinityGroupId(flowId).build();
    }
}
