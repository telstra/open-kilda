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

package org.openkilda.wfm.converter;

import static org.junit.Assert.assertEquals;

import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.model.FlowPair;
import org.openkilda.messaging.model.SwitchId;

import org.junit.Test;
import org.mapstruct.factory.Mappers;

import java.util.ArrayList;
import java.util.List;

public class FlowMapperTest {

    private static final FlowMapper FLOW_MAPPER = Mappers.getMapper(FlowMapper.class);

    @Test
    public void testFlowPairToDto() {

        PathInfoData pathInfoData = new PathInfoData();
        pathInfoData.setLatency(1L);
        List<PathNode> path = new ArrayList<>();
        PathNode pathNode = new PathNode(new SwitchId(1), 1, 1, 1L, 1L);
        path.add(pathNode);
        pathNode = new PathNode(new SwitchId(2), 2, 2, 2L, 2L);
        path.add(pathNode);
        pathInfoData.setPath(path);
        Flow flow = new Flow();
        flow.setSourceSwitch(new SwitchId(1L));
        flow.setDestinationSwitch(new SwitchId(2L));
        flow.setFlowPath(pathInfoData);
        flow.setFlowId("12");
        flow.setCookie(11);
        flow.setSourcePort(113);
        flow.setSourceVlan(1112);
        flow.setDestinationPort(113);
        flow.setDestinationVlan(1112);
        flow.setBandwidth(23);
        flow.setDescription("SOME FLOW");
        flow.setLastUpdated("SOME LAST UPDATED FLOW");
        flow.setTransitVlan(87);
        flow.setMeterId(65);
        flow.setIgnoreBandwidth(true);
        flow.setPeriodicPings(true);
        FlowPair<Flow, Flow> pair = new FlowPair<>(flow, flow);
        org.openkilda.model.FlowPair p =  FLOW_MAPPER.flowPairFromDto(pair);
        assertEquals(p.getForward().getFlowId(), pair.getLeft().getFlowId());
    }
}
