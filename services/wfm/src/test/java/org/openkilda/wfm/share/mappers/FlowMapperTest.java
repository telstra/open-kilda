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

package org.openkilda.wfm.share.mappers;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;

import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.messaging.model.FlowDto;
import org.openkilda.messaging.model.FlowPairDto;
import org.openkilda.model.FlowPair;
import org.openkilda.model.SwitchId;

import org.junit.Test;

public class FlowMapperTest {
    private static final SwitchId SRC_SWITCH_ID = new SwitchId("00:00:00:00:00:00:00:01");
    private static final SwitchId DST_SWITCH_ID = new SwitchId("00:00:00:00:00:00:00:02");


    @Test
    public void testFlowPairToDto() {
        PathInfoData pathInfoData = new PathInfoData();
        pathInfoData.setLatency(1L);
        pathInfoData.setPath(asList(
                new PathNode(SRC_SWITCH_ID, 1, 1, 1L, 1L),
                new PathNode(DST_SWITCH_ID, 2, 2, 2L, 2L)
        ));

        FlowDto forwardFlow = new FlowDto();
        forwardFlow.setSourceSwitch(SRC_SWITCH_ID);
        forwardFlow.setDestinationSwitch(DST_SWITCH_ID);
        forwardFlow.setFlowPath(pathInfoData);
        forwardFlow.setFlowId("12");
        forwardFlow.setCookie(11);
        forwardFlow.setSourcePort(113);
        forwardFlow.setSourceVlan(1112);
        forwardFlow.setDestinationPort(113);
        forwardFlow.setDestinationVlan(1112);
        forwardFlow.setBandwidth(23);
        forwardFlow.setDescription("SOME FLOW");
        forwardFlow.setLastUpdated("2011-12-03T10:15:30Z");
        forwardFlow.setTransitVlan(87);
        forwardFlow.setMeterId(65);
        forwardFlow.setIgnoreBandwidth(true);
        forwardFlow.setPeriodicPings(true);

        PathInfoData reversePathInfoData = new PathInfoData();
        reversePathInfoData.setLatency(1L);
        reversePathInfoData.setPath(asList(
                new PathNode(DST_SWITCH_ID, 2, 2, 2L, 2L),
                new PathNode(SRC_SWITCH_ID, 1, 1, 1L, 1L)
        ));

        FlowDto reverseFlow = new FlowDto();
        reverseFlow.setSourceSwitch(forwardFlow.getDestinationSwitch());
        reverseFlow.setDestinationSwitch(SRC_SWITCH_ID);
        reverseFlow.setFlowPath(reversePathInfoData);
        reverseFlow.setFlowId("12");
        reverseFlow.setCookie(12);
        reverseFlow.setSourcePort(113);
        reverseFlow.setSourceVlan(1112);
        reverseFlow.setDestinationPort(113);
        reverseFlow.setDestinationVlan(1112);
        reverseFlow.setBandwidth(23);
        reverseFlow.setDescription("SOME FLOW");
        reverseFlow.setLastUpdated("2011-12-03T10:15:30Z");
        reverseFlow.setTransitVlan(88);
        reverseFlow.setMeterId(66);
        reverseFlow.setIgnoreBandwidth(true);
        reverseFlow.setPeriodicPings(true);

        FlowPairDto<FlowDto, FlowDto> pair = new FlowPairDto<>(forwardFlow, reverseFlow);
        FlowPair p = FlowMapper.INSTANCE.map(pair);
        assertEquals(p.getForward().getFlowId(), pair.getLeft().getFlowId());
    }
}
