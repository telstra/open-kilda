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

package org.openkilda.wfm.topology.switchmanager.service.impl;

import static java.util.Arrays.asList;
import static java.util.Collections.singleton;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.openkilda.messaging.command.flow.RemoveFlow;
import org.openkilda.messaging.command.switches.DeleteRulesCriteria;
import org.openkilda.messaging.info.rule.FlowApplyActions;
import org.openkilda.messaging.info.rule.FlowEntry;
import org.openkilda.messaging.info.rule.FlowInstructions;
import org.openkilda.messaging.info.rule.FlowMatchField;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathDirection;
import org.openkilda.model.MacAddress;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchProperties;
import org.openkilda.model.TransitVlan;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchPropertiesRepository;
import org.openkilda.persistence.repositories.TransitVlanRepository;

import org.junit.Test;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

public class CommandBuilderImplTest {

    private static final SwitchId SWITCH_ID_A = new SwitchId("00:10");
    private static final SwitchId SWITCH_ID_B = new SwitchId("00:20");
    private static final SwitchId SWITCH_ID_C = new SwitchId("00:30");
    private static final MacAddress SERVER42_MAC_ADDRESS = new MacAddress("42:42:42:42:42:42");
    private static final int SERVER42_PORT = 1;
    private static final int SERVER42_VLAN = 2;

    private static CommandBuilderImpl commandBuilder = new CommandBuilderImpl(persistenceManager().build());

    private static PersistenceManagerBuilder persistenceManager() {
        return new PersistenceManagerBuilder();
    }

    private static class PersistenceManagerBuilder {
        private FlowRepository flowRepository = mock(FlowRepository.class);
        private FlowPathRepository flowPathRepository = mock(FlowPathRepository.class);
        private TransitVlanRepository transitVlanRepository = mock(TransitVlanRepository.class);
        private SwitchPropertiesRepository switchPropertiesRepository = mock(SwitchPropertiesRepository.class);

        private FlowPath buildFlowAndPath(String flowId, SwitchId srcSwitchId, SwitchId destSwitchId,
                                          int cookie, int transitVlan) {
            boolean forward = srcSwitchId.compareTo(destSwitchId) <= 0;
            Switch srcSwitch = Switch.builder().switchId(srcSwitchId).build();
            Switch destSwitch = Switch.builder().switchId(destSwitchId).build();

            Flow flow = Flow.builder()
                    .flowId(flowId)
                    .srcSwitch(forward ? srcSwitch : destSwitch)
                    .destSwitch(forward ? destSwitch : srcSwitch)
                    .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                    .build();

            FlowPath forwardPath = FlowPath.builder()
                    .pathId(new PathId(String.format(
                            "(%s-%s)--%s", srcSwitchId.toOtsdFormat(), destSwitchId.toOtsdFormat(), UUID.randomUUID())))
                    .srcSwitch(srcSwitch)
                    .destSwitch(destSwitch)
                    .cookie(new FlowSegmentCookie(FlowPathDirection.FORWARD, cookie))
                    .build();
            FlowPath reversePath = FlowPath.builder()
                    .pathId(new PathId(String.format(
                            "(%s-%s)--%s", destSwitchId.toOtsdFormat(), srcSwitchId.toOtsdFormat(), UUID.randomUUID())))
                    .srcSwitch(destSwitch)
                    .destSwitch(srcSwitch)
                    .cookie(new FlowSegmentCookie(FlowPathDirection.REVERSE, cookie))
                    .build();
            flow.setForwardPath(forward ? forwardPath : reversePath);
            flow.setReversePath(forward ? reversePath : forwardPath);

            when(flowPathRepository.findById(eq(forwardPath.getPathId())))
                    .thenReturn(Optional.of(forwardPath));
            when(flowPathRepository.findById(eq(reversePath.getPathId())))
                    .thenReturn(Optional.of(reversePath));
            when(flowPathRepository.findByFlowId(eq(flowId)))
                    .thenReturn(asList(forwardPath, reversePath));
            when(flowRepository.findById(eq(flowId)))
                    .thenReturn(Optional.of(flow));

            TransitVlan transitVlanEntity = TransitVlan.builder()
                    .flowId(flow.getFlowId())
                    .pathId(forwardPath.getPathId())
                    .vlan(transitVlan)
                    .build();
            when(transitVlanRepository.findByPathId(eq(forwardPath.getPathId()), any()))
                    .thenReturn(singleton(transitVlanEntity));
            when(switchPropertiesRepository.findBySwitchId(any()))
                    .thenReturn(Optional.ofNullable(SwitchProperties.builder()
                            .server42FlowRtt(true)
                            .server42MacAddress(SERVER42_MAC_ADDRESS)
                            .server42Port(SERVER42_PORT)
                            .server42Vlan(SERVER42_VLAN)
                            .build()));

            return forwardPath;
        }

        private PathSegment buildSegment(PathId pathId, SwitchId srcSwitchId, SwitchId destSwitchId) {
            return PathSegment.builder()
                    .pathId(pathId)
                    .srcSwitch(Switch.builder().switchId(srcSwitchId).build())
                    .destSwitch(Switch.builder().switchId(destSwitchId).build())
                    .build();
        }

        private PersistenceManager build() {
            FlowPath flowPathA = buildFlowAndPath("A", SWITCH_ID_A, SWITCH_ID_B, 1, 1);
            flowPathA.setSegments(asList(buildSegment(flowPathA.getPathId(), SWITCH_ID_A, SWITCH_ID_C),
                    buildSegment(flowPathA.getPathId(), SWITCH_ID_C, SWITCH_ID_B)));

            FlowPath flowPathB = buildFlowAndPath("B", SWITCH_ID_A, SWITCH_ID_C, 2, 1);
            flowPathB.setSegments(asList(buildSegment(flowPathB.getPathId(), SWITCH_ID_A, SWITCH_ID_B),
                    buildSegment(flowPathB.getPathId(), SWITCH_ID_B, SWITCH_ID_C)));

            FlowPath flowPathC = buildFlowAndPath("C", SWITCH_ID_A, SWITCH_ID_A, 3, 1);

            FlowPath flowPathD = buildFlowAndPath("D", SWITCH_ID_B, SWITCH_ID_A, 4, 1);
            flowPathD.setSegments(asList(buildSegment(flowPathD.getPathId(), SWITCH_ID_B, SWITCH_ID_A)));

            when(flowPathRepository.findBySegmentDestSwitch(eq(SWITCH_ID_B)))
                    .thenReturn(Arrays.asList(flowPathA, flowPathB));
            when(flowPathRepository.findByEndpointSwitch(eq(SWITCH_ID_B)))
                    .thenReturn(Arrays.asList(flowPathC, flowPathD));

            RepositoryFactory repositoryFactory = mock(RepositoryFactory.class);
            when(repositoryFactory.createFlowRepository()).thenReturn(flowRepository);
            when(repositoryFactory.createFlowPathRepository()).thenReturn(flowPathRepository);
            when(repositoryFactory.createTransitVlanRepository()).thenReturn(transitVlanRepository);
            when(repositoryFactory.createSwitchPropertiesRepository()).thenReturn(switchPropertiesRepository);

            PersistenceManager persistenceManager = mock(PersistenceManager.class);
            when(persistenceManager.getRepositoryFactory()).thenReturn(repositoryFactory);
            return persistenceManager;
        }
    }

    @Test
    public void shouldBuildRemoveFlowWithoutMeterFromFlowEntryWithTransitVlanEncapsulation() {
        Long cookie = new FlowSegmentCookie(FlowPathDirection.FORWARD, 1).getValue();
        String inPort = "1";
        String inVlan = "10";
        String outPort = "2";
        FlowEntry flowEntry = buildFlowEntry(cookie, inPort, inVlan, outPort, null, false, null, null);

        RemoveFlow removeFlow = commandBuilder.buildRemoveFlowWithoutMeterFromFlowEntry(SWITCH_ID_A, flowEntry);
        assertEquals(cookie, removeFlow.getCookie());

        DeleteRulesCriteria criteria = removeFlow.getCriteria();
        assertEquals(cookie, criteria.getCookie());
        assertEquals(Integer.valueOf(inPort), criteria.getInPort());
        assertEquals(Integer.valueOf(inVlan), criteria.getEncapsulationId());
        assertEquals(Integer.valueOf(outPort), criteria.getOutPort());
        assertNull(criteria.getMetadataValue());
        assertNull(criteria.getMetadataMask());
    }

    @Test
    public void shouldBuildRemoveFlowWithoutMeterFromFlowEntryWithStringOutPort() {
        Long cookie = new FlowSegmentCookie(FlowPathDirection.FORWARD, 1).getValue();
        String inPort = "1";
        String inVlan = "10";
        String outPort = "in_port";
        FlowEntry flowEntry = buildFlowEntry(cookie, inPort, inVlan, outPort, null, false, null, null);

        RemoveFlow removeFlow = commandBuilder.buildRemoveFlowWithoutMeterFromFlowEntry(SWITCH_ID_A, flowEntry);
        assertEquals(cookie, removeFlow.getCookie());

        DeleteRulesCriteria criteria = removeFlow.getCriteria();
        assertEquals(cookie, criteria.getCookie());
        assertEquals(Integer.valueOf(inPort), criteria.getInPort());
        assertEquals(Integer.valueOf(inVlan), criteria.getEncapsulationId());
        assertNull(criteria.getOutPort());
        assertNull(criteria.getMetadataValue());
        assertNull(criteria.getMetadataMask());
    }

    @Test
    public void shouldBuildRemoveFlowWithoutMeterFromFlowEntryWithVxlanEncapsulationIngress() {
        Long cookie = new FlowSegmentCookie(FlowPathDirection.FORWARD, 1).getValue();
        String inPort = "1";
        String outPort = "2";
        String tunnelId = "10";
        String metadataValue = "0x15";
        String metadataMask = "0xFF";
        FlowEntry flowEntry = buildFlowEntry(cookie, inPort, null, outPort, tunnelId, true, metadataValue,
                metadataMask);

        RemoveFlow removeFlow = commandBuilder.buildRemoveFlowWithoutMeterFromFlowEntry(SWITCH_ID_A, flowEntry);
        assertEquals(cookie, removeFlow.getCookie());

        DeleteRulesCriteria criteria = removeFlow.getCriteria();
        assertEquals(cookie, criteria.getCookie());
        assertEquals(Integer.valueOf(inPort), criteria.getInPort());
        assertNull(criteria.getEncapsulationId());
        assertEquals(Integer.valueOf(outPort), criteria.getOutPort());
        assertEquals(Long.decode(metadataValue), criteria.getMetadataValue());
        assertEquals(Long.decode(metadataMask), criteria.getMetadataMask());
    }

    @Test
    public void shouldBuildRemoveFlowWithoutMeterFromFlowEntryWithVxlanEncapsulationTransitAndEgress() {
        Long cookie = new FlowSegmentCookie(FlowPathDirection.FORWARD, 1).getValue();
        String inPort = "1";
        String outPort = "2";
        String tunnelId = "10";
        FlowEntry flowEntry = buildFlowEntry(cookie, inPort, null, outPort, tunnelId, false, null, null);

        RemoveFlow removeFlow = commandBuilder.buildRemoveFlowWithoutMeterFromFlowEntry(SWITCH_ID_A, flowEntry);
        assertEquals(cookie, removeFlow.getCookie());

        DeleteRulesCriteria criteria = removeFlow.getCriteria();
        assertEquals(cookie, criteria.getCookie());
        assertEquals(Integer.valueOf(inPort), criteria.getInPort());
        assertEquals(Integer.valueOf(tunnelId), criteria.getEncapsulationId());
        assertEquals(Integer.valueOf(outPort), criteria.getOutPort());
        assertNull(criteria.getMetadataValue());
        assertNull(criteria.getMetadataMask());
    }

    private FlowEntry buildFlowEntry(Long cookie, String inPort, String inVlan, String outPort,
                                     String tunnelId, boolean tunnelIdIngressRule, String metadataValue,
                                     String metadataMask) {
        return FlowEntry.builder()
                .cookie(cookie)
                .match(FlowMatchField.builder()
                        .inPort(inPort)
                        .vlanVid(inVlan)
                        .metadataValue(metadataValue)
                        .metadataMask(metadataMask)
                        .tunnelId(!tunnelIdIngressRule ? tunnelId : null)
                        .build())
                .instructions(FlowInstructions.builder()
                        .applyActions(FlowApplyActions.builder()
                                .flowOutput(outPort)
                                .pushVxlan(tunnelIdIngressRule ? tunnelId : null)
                                .build())
                        .build())
                .build();
    }
}
