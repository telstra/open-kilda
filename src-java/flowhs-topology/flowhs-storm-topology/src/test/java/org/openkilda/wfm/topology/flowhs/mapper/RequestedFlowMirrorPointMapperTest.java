/* Copyright 2021 Telstra Open Source
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

import org.openkilda.messaging.command.flow.FlowMirrorPointCreateRequest;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowPathDirection;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.topology.flowhs.model.RequestedFlowMirrorPoint;

import org.junit.Test;

public class RequestedFlowMirrorPointMapperTest {

    public static final String FLOW_ID = "flow_id";
    private static final String MIRROR_POINT_ID = "mirror_point_id";
    private static final FlowPathDirection MIRROR_POINT_DIRECTION = FlowPathDirection.FORWARD;
    public static final SwitchId MIRROR_POINT_SWITCH_ID = new SwitchId("1");
    public static final SwitchId RECEIVER_POINT_SWITCH_ID = new SwitchId("2");
    public static final int RECEIVER_POINT_PORT = 2;
    public static final int RECEIVER_POINT_OUTER_VLAN = 4;
    public static final int RECEIVER_POINT_INNER_VLAN = 6;

    @Test
    public void testFlowMirrorPointCreateRequestToRequestedFlowMirrorPoint() {
        FlowMirrorPointCreateRequest request = FlowMirrorPointCreateRequest.builder()
                .flowId(FLOW_ID)
                .mirrorPointId(MIRROR_POINT_ID)
                .mirrorPointDirection(MIRROR_POINT_DIRECTION)
                .mirrorPointSwitchId(MIRROR_POINT_SWITCH_ID)
                .sinkEndpoint(FlowEndpoint.builder()
                        .switchId(RECEIVER_POINT_SWITCH_ID)
                        .portNumber(RECEIVER_POINT_PORT)
                        .outerVlanId(RECEIVER_POINT_OUTER_VLAN)
                        .innerVlanId(RECEIVER_POINT_INNER_VLAN)
                        .build())
                .build();

        RequestedFlowMirrorPoint flowMirrorPoint = RequestedFlowMirrorPointMapper.INSTANCE.map(request);

        assertEquals(request.getFlowId(), flowMirrorPoint.getFlowId());
        assertEquals(request.getMirrorPointId(), flowMirrorPoint.getMirrorPointId());
        assertEquals(MIRROR_POINT_DIRECTION, flowMirrorPoint.getMirrorPointDirection());
        assertEquals(request.getMirrorPointSwitchId(), flowMirrorPoint.getMirrorPointSwitchId());
        assertEquals(request.getSinkEndpoint(), flowMirrorPoint.getSinkEndpoint());
    }
}
