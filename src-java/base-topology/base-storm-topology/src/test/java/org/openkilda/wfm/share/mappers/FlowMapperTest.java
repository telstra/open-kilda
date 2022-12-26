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

package org.openkilda.wfm.share.mappers;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.messaging.model.DetectConnectedDevicesDto;
import org.openkilda.messaging.model.FlowDto;
import org.openkilda.messaging.model.FlowPairDto;
import org.openkilda.messaging.model.MirrorPointStatusDto;
import org.openkilda.messaging.payload.flow.FlowEncapsulationType;
import org.openkilda.model.DetectConnectedDevices;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowMirror;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathDirection;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.KildaConfiguration;
import org.openkilda.model.PathId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.FlowSegmentCookie;

import com.google.common.base.Functions;
import com.google.common.collect.Lists;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class FlowMapperTest {
    private static final SwitchId SRC_SWITCH_ID = new SwitchId("00:00:00:00:00:00:00:01");
    private static final SwitchId DST_SWITCH_ID = new SwitchId("00:00:00:00:00:00:00:02");
    private static final String MIRROR_ID_A = "mirror_id_a";
    private static final String MIRROR_ID_B = "mirror_id_b";
    private static final FlowPathStatus FLOW_MIRROR_STATUS_A = FlowPathStatus.ACTIVE;
    private static final FlowPathStatus FLOW_MIRROR_STATUS_B = FlowPathStatus.ACTIVE;

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
        forwardFlow.setFlowId("12");
        forwardFlow.setCookie(11);
        forwardFlow.setSourcePort(113);
        forwardFlow.setSourceVlan(1112);
        forwardFlow.setDestinationPort(113);
        forwardFlow.setDestinationVlan(1112);
        forwardFlow.setBandwidth(23);
        forwardFlow.setDescription("SOME FLOW");
        forwardFlow.setLastUpdated("2011-12-03T10:15:30Z");
        forwardFlow.setTransitEncapsulationId(87);
        forwardFlow.setMeterId(65);
        forwardFlow.setIgnoreBandwidth(true);
        forwardFlow.setPeriodicPings(true);
        forwardFlow.setEncapsulationType(FlowEncapsulationType.TRANSIT_VLAN);
        forwardFlow.setDetectConnectedDevices(new DetectConnectedDevicesDto(
                false, true, true, false, false, false, true, true));

        PathInfoData reversePathInfoData = new PathInfoData();
        reversePathInfoData.setLatency(1L);
        reversePathInfoData.setPath(asList(
                new PathNode(DST_SWITCH_ID, 2, 2, 2L, 2L),
                new PathNode(SRC_SWITCH_ID, 1, 1, 1L, 1L)
        ));

        FlowDto reverseFlow = new FlowDto();
        reverseFlow.setSourceSwitch(forwardFlow.getDestinationSwitch());
        reverseFlow.setDestinationSwitch(SRC_SWITCH_ID);
        reverseFlow.setFlowId("12");
        reverseFlow.setCookie(12);
        reverseFlow.setSourcePort(113);
        reverseFlow.setSourceVlan(1112);
        reverseFlow.setDestinationPort(113);
        reverseFlow.setDestinationVlan(1112);
        reverseFlow.setBandwidth(23);
        reverseFlow.setDescription("SOME FLOW");
        reverseFlow.setLastUpdated("2011-12-03T10:15:30Z");
        reverseFlow.setTransitEncapsulationId(88);
        reverseFlow.setMeterId(66);
        reverseFlow.setIgnoreBandwidth(true);
        reverseFlow.setPeriodicPings(true);
        reverseFlow.setEncapsulationType(FlowEncapsulationType.TRANSIT_VLAN);
        reverseFlow.setDetectConnectedDevices(new DetectConnectedDevicesDto(
                true, false, false, true, false, false, true, true));

        FlowPairDto<FlowDto, FlowDto> pair = new FlowPairDto<>(forwardFlow, reverseFlow);
        Flow p = FlowMapper.INSTANCE.map(pair, () -> KildaConfiguration.DEFAULTS);
        assertEquals(p.getFlowId(), pair.getLeft().getFlowId());
        assertDetectConnectedDevices(forwardFlow.getDetectConnectedDevices(), p.getDetectConnectedDevices());
    }

    private void assertDetectConnectedDevices(DetectConnectedDevicesDto expected, DetectConnectedDevices actual) {
        assertEquals(expected.isSrcLldp(), actual.isSrcLldp());
        assertEquals(expected.isSrcArp(), actual.isSrcArp());
        assertEquals(expected.isDstLldp(), actual.isDstLldp());
        assertEquals(expected.isDstArp(), actual.isDstArp());
    }

    @Test
    public void testStatusDetailsMapping() {
        Flow flow = buildFlow();

        FlowDto flowDto = FlowMapper.INSTANCE.map(flow);

        assertNotNull(flowDto.getFlowStatusDetails());
        assertEquals(FlowPathStatus.ACTIVE, flowDto.getFlowStatusDetails().getMainFlowPathStatus());
        assertEquals(FlowPathStatus.INACTIVE, flowDto.getFlowStatusDetails().getProtectedFlowPathStatus());
        assertDetectConnectedDevices(flowDto.getDetectConnectedDevices(), flow.getDetectConnectedDevices());
    }

    @Test
    public void testMirrorPointStatusesMapping() {
        Flow flow = buildFlow();

        FlowDto flowDto = FlowMapper.INSTANCE.map(flow, new HashSet<>(), new HashSet<>(), buildFlowMirrorList());

        assertNotNull(flowDto.getMirrorPointStatuses());
        assertEquals(2, flowDto.getMirrorPointStatuses().size());

        Map<String, MirrorPointStatusDto> statusMap = flowDto.getMirrorPointStatuses().stream()
                .collect(Collectors.toMap(MirrorPointStatusDto::getMirrorPointId, Functions.identity()));

        assertEquals(FLOW_MIRROR_STATUS_A.toString().toLowerCase(), statusMap.get(MIRROR_ID_A).getStatus());
        assertEquals(FLOW_MIRROR_STATUS_B.toString().toLowerCase(), statusMap.get(MIRROR_ID_B).getStatus());
    }

    private Flow buildFlow() {
        Flow flow = Flow.builder()
                .flowId("test_flow")
                .srcSwitch(Switch.builder().switchId(SRC_SWITCH_ID).build())
                .destSwitch(Switch.builder().switchId(DST_SWITCH_ID).build())
                .allocateProtectedPath(true)
                .build();

        FlowPath forwardFlowPath = FlowPath.builder()
                .pathId(new PathId("forward_flow_path"))
                .srcSwitch(Switch.builder().switchId(SRC_SWITCH_ID).build())
                .destSwitch(Switch.builder().switchId(DST_SWITCH_ID).build())
                .cookie(new FlowSegmentCookie(FlowPathDirection.FORWARD, 1))
                .status(FlowPathStatus.ACTIVE)
                .build();
        flow.setForwardPath(forwardFlowPath);

        FlowPath reverseFlowPath = FlowPath.builder()
                .pathId(new PathId("reverse_flow_path"))
                .srcSwitch(Switch.builder().switchId(DST_SWITCH_ID).build())
                .destSwitch(Switch.builder().switchId(SRC_SWITCH_ID).build())
                .cookie(new FlowSegmentCookie(FlowPathDirection.REVERSE, 1))
                .status(FlowPathStatus.ACTIVE)
                .build();
        flow.setReversePath(reverseFlowPath);

        FlowPath forwardProtectedFlowPath = FlowPath.builder()
                .pathId(new PathId("forward_protected_flow_path"))
                .srcSwitch(Switch.builder().switchId(SRC_SWITCH_ID).build())
                .destSwitch(Switch.builder().switchId(DST_SWITCH_ID).build())
                .cookie(new FlowSegmentCookie(FlowPathDirection.FORWARD, 2))
                .status(FlowPathStatus.INACTIVE)
                .build();
        flow.setProtectedForwardPath(forwardProtectedFlowPath);

        FlowPath reverseProtectedFlowPath = FlowPath.builder()
                .pathId(new PathId("reverse_protected_flow_path"))
                .srcSwitch(Switch.builder().switchId(DST_SWITCH_ID).build())
                .destSwitch(Switch.builder().switchId(SRC_SWITCH_ID).build())
                .cookie(new FlowSegmentCookie(FlowPathDirection.REVERSE, 2))
                .status(FlowPathStatus.INACTIVE)
                .build();
        flow.setProtectedReversePath(reverseProtectedFlowPath);
        return flow;
    }

    private List<FlowMirror> buildFlowMirrorList() {
        FlowMirror flowMirrorPathA = FlowMirror.builder()
                .flowMirrorId(MIRROR_ID_A)
                .mirrorSwitch(Switch.builder().switchId(SRC_SWITCH_ID).build())
                .egressSwitch(Switch.builder().switchId(SRC_SWITCH_ID).build())
                .status(FLOW_MIRROR_STATUS_A)
                .build();

        FlowMirror flowMirrorPathB = FlowMirror.builder()
                .flowMirrorId(MIRROR_ID_B)
                .mirrorSwitch(Switch.builder().switchId(SRC_SWITCH_ID).build())
                .egressSwitch(Switch.builder().switchId(SRC_SWITCH_ID).build())
                .status(FLOW_MIRROR_STATUS_B)
                .build();

        return Lists.newArrayList(flowMirrorPathA, flowMirrorPathB);
    }
}
