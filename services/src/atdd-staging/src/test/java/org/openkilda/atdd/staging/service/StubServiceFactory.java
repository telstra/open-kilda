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

package org.openkilda.atdd.staging.service;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.openkilda.messaging.info.event.IslChangeType;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.messaging.info.event.SwitchChangeType;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.model.FlowDto;
import org.openkilda.messaging.model.FlowPairDto;
import org.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.messaging.payload.flow.FlowPayloadToFlowConverter;
import org.openkilda.messaging.payload.flow.FlowState;
import org.openkilda.model.SwitchId;
import org.openkilda.northbound.dto.switches.RulesSyncResult;
import org.openkilda.northbound.dto.switches.RulesValidationResult;
import org.openkilda.testing.model.topology.TopologyDefinition;
import org.openkilda.testing.model.topology.TopologyDefinition.Switch;
import org.openkilda.testing.service.floodlight.FloodlightService;
import org.openkilda.testing.service.floodlight.model.FlowApplyActions;
import org.openkilda.testing.service.floodlight.model.FlowEntriesMap;
import org.openkilda.testing.service.floodlight.model.FlowEntry;
import org.openkilda.testing.service.floodlight.model.FlowInstructions;
import org.openkilda.testing.service.floodlight.model.FlowMatchField;
import org.openkilda.testing.service.floodlight.model.MeterBand;
import org.openkilda.testing.service.floodlight.model.MeterEntry;
import org.openkilda.testing.service.floodlight.model.MetersEntriesMap;
import org.openkilda.testing.service.floodlight.model.SwitchEntry;
import org.openkilda.testing.service.lockkeeper.LockKeeperService;
import org.openkilda.testing.service.lockkeeper.model.ASwitchFlow;
import org.openkilda.testing.service.northbound.NorthboundService;
import org.openkilda.testing.service.traffexam.TraffExamService;
import org.openkilda.testing.service.traffexam.model.Bandwidth;
import org.openkilda.testing.service.traffexam.model.Exam;
import org.openkilda.testing.service.traffexam.model.ExamReport;
import org.openkilda.testing.service.traffexam.model.Host;

import org.apache.commons.lang3.SerializationUtils;
import org.mockito.stubbing.Answer;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * A factory for stub implementations of services. This is used by unit tests to imitate correct behaviour of Kilda
 * components.
 */
public class StubServiceFactory {

    private final Map<String, FlowPayload> flowPayloads = new HashMap<>();
    private final Map<String, FlowPairDto<FlowDto, FlowDto>> flows = new HashMap<>();
    private int meterCounter = 1;

    private final TopologyDefinition topologyDefinition;

    public StubServiceFactory(TopologyDefinition topologyDefinition) {
        this.topologyDefinition = topologyDefinition;
    }

    /**
     * Get a stub for {@link FloodlightService}. The instance is tied to the factory state.
     */
    public FloodlightService getFloodlightStub() {
        FloodlightService serviceMock = mock(FloodlightService.class);

        when(serviceMock.getFlows(any()))
                .thenAnswer(invocation -> {
                    SwitchId switchId = (SwitchId) invocation.getArguments()[0];
                    String switchVersion = topologyDefinition.getActiveSwitches().stream()
                            .filter(sw -> sw.getDpId().equals(switchId))
                            .map(Switch::getOfVersion)
                            .findAny()
                            .orElse("OF_13");

                    return buildFlowEntries(switchId, switchVersion);
                });

        when(serviceMock.getMeters(any()))
                .then((Answer<MetersEntriesMap>) invocation -> {
                    SwitchId switchId = (SwitchId) invocation.getArguments()[0];

                    MetersEntriesMap result = new MetersEntriesMap();
                    flows.values().forEach(flowPair -> {
                        if (flowPair.getLeft().getSourceSwitch().equals(switchId)
                                || flowPair.getRight().getSourceSwitch().equals(switchId)) {

                            MeterEntry entry = new MeterEntry(emptyList(), flowPair.getLeft().getMeterId(),
                                    singletonList(new MeterBand(flowPair.getLeft().getBandwidth(), 0, "", 1)), "");
                            result.put(entry.getMeterId(), entry);
                        }
                    });

                    return result;
                });

        when(serviceMock.getSwitches())
                .then((Answer<List<SwitchEntry>>) invocation -> topologyDefinition.getActiveSwitches().stream()
                        .map(sw -> SwitchEntry.builder().switchId(sw.getDpId()).ofVersion(sw.getOfVersion()).build())
                        .collect(toList()));

        return serviceMock;
    }

    /**
     * Get a stub for {@link LockKeeperService}. The instance is tied to the factory state.
     */
    public LockKeeperService getLockKeeperStub() {
        LockKeeperService serviceMock = mock(LockKeeperService.class);
        List<ASwitchFlow> aswitchFlows = topologyDefinition.getIslsForActiveSwitches().stream()
                .filter(isl -> isl.getAswitch() != null)
                .map(isl -> {
                    TopologyDefinition.ASwitch asw = isl.getAswitch();
                    return Arrays.asList(new ASwitchFlow(asw.getInPort(), asw.getOutPort()),
                            new ASwitchFlow(asw.getOutPort(), asw.getInPort()));
                }).flatMap(List::stream).collect(toList());

        when(serviceMock.getAllFlows())
                .thenReturn(aswitchFlows);
        return serviceMock;
    }

    private FlowEntriesMap buildFlowEntries(SwitchId switchId, String switchVersion) {
        FlowEntriesMap result = new FlowEntriesMap();

        //broadcast verification flow (for all OF versions)
        FlowEntry flowEntry = buildFlowEntry("flow-0x8000000000000002",
                FlowMatchField.builder().ethDst("08:ed:02:ef:ff:ff").build(),
                FlowInstructions.builder().applyActions(
                        FlowApplyActions.builder()
                                .flowOutput("controller")
                                .field(switchId.toMacAddress() + "->eth_dst").build()
                ).build()
        );
        result.put(flowEntry.getCookie(), flowEntry);

        //define drop flow
        FlowEntry dropFlow = FlowEntry.builder()
                .instructions(FlowInstructions.builder().none("drop").build())
                .priority(1)
                .cookie("flow-0x8000000000000001")
                .build();
        result.put(dropFlow.getCookie(), dropFlow);

        if ("OF_13".equals(switchVersion)) {
            //non-broadcast flow for versions 13 and later
            FlowEntry flowFor13Version = buildFlowEntry("flow-0x8000000000000003",
                    FlowMatchField.builder().ethDst(switchId.toMacAddress()).build(),
                    FlowInstructions.builder().applyActions(
                            FlowApplyActions.builder()
                                    .flowOutput("controller")
                                    .field(switchId.toMacAddress() + "->eth_dst")
                                    .build()
                    ).build()
            );
            result.put(flowFor13Version.getCookie(), flowFor13Version);
        }

        return result;
    }

    private FlowEntry buildFlowEntry(String cookie, FlowMatchField match, FlowInstructions instructions) {
        return FlowEntry.builder()
                .instructions(instructions)
                .match(match)
                .cookie(cookie)
                .build();
    }

    /**
     * Get a stub for {@link NorthboundService}. The instance is tied to the factory state.
     */
    public NorthboundService getNorthboundStub() {
        NorthboundService serviceMock = mock(NorthboundService.class);

        when(serviceMock.getActiveSwitches())
                .thenAnswer(invocation -> topologyDefinition.getActiveSwitches().stream()
                        .map(sw -> new SwitchInfoData(sw.getDpId(),
                                SwitchChangeType.ACTIVATED, "", "", "", "", false))
                        .collect(toList()));

        when(serviceMock.getActiveLinks())
                .thenAnswer(invocation -> topologyDefinition.getIslsForActiveSwitches().stream()
                        .flatMap(link -> Stream.of(
                                new IslInfoData(0,
                                        new PathNode(link.getSrcSwitch().getDpId(),
                                                link.getSrcPort(), 0),
                                        new PathNode(link.getDstSwitch().getDpId(),
                                                link.getDstPort(), 1),
                                        link.getMaxBandwidth(), IslChangeType.DISCOVERED, 0, false),
                                new IslInfoData(0,
                                        new PathNode(link.getDstSwitch().getDpId(),
                                                link.getDstPort(), 0),
                                        new PathNode(link.getSrcSwitch().getDpId(),
                                                link.getSrcPort(), 1),
                                        link.getMaxBandwidth(), IslChangeType.DISCOVERED, 0, false)
                        ))
                        .collect(toList()));

        when(serviceMock.getAllFlows())
                .thenReturn(new ArrayList<>(flowPayloads.values()));

        when(serviceMock.getFlow(any()))
                .thenAnswer(invocation -> {
                    String flowId = (String) invocation.getArguments()[0];
                    return flowPayloads.containsKey(flowId) ? SerializationUtils.clone(flowPayloads.get(flowId)) : null;
                });

        when(serviceMock.getFlowStatus(any()))
                .thenAnswer(invocation -> {
                    String flowId = (String) invocation.getArguments()[0];
                    return flows.containsKey(flowId) ? new FlowIdStatusPayload(flowId, FlowState.UP) : null;
                });

        when(serviceMock.addFlow(any()))
                .thenAnswer(invocation -> {
                    FlowPayload result = SerializationUtils.clone(((FlowPayload) invocation.getArguments()[0]));
                    result.setLastUpdated(LocalTime.now().toString());
                    result.setStatus(FlowState.IN_PROGRESS.toString());
                    putFlow(result.getId(), result);
                    return result;
                });

        when(serviceMock.updateFlow(any(), any()))
                .thenAnswer(invocation -> {
                    String flowId = (String) invocation.getArguments()[0];
                    FlowPayload result = SerializationUtils.clone(((FlowPayload) invocation.getArguments()[1]));
                    result.setLastUpdated(LocalTime.now().toString());
                    putFlow(flowId, result);
                    return result;
                });

        when(serviceMock.deleteFlow(any()))
                .thenAnswer(invocation -> {
                    String flowId = (String) invocation.getArguments()[0];
                    flows.remove(flowId);
                    return flowPayloads.remove(flowId);
                });

        when(serviceMock.synchronizeSwitchRules(any()))
                .thenReturn(new RulesSyncResult(emptyList(), emptyList(), emptyList(), emptyList()));

        when(serviceMock.validateSwitchRules(any()))
                .thenReturn(new RulesValidationResult(emptyList(), emptyList(), emptyList()));

        return serviceMock;
    }

    private void putFlow(String flowId, FlowPayload flowPayload) {
        flowPayloads.put(flowId, flowPayload);

        FlowDto forwardFlow = FlowPayloadToFlowConverter.buildFlowByFlowPayload(flowPayload);
        forwardFlow.setMeterId(meterCounter++);

        FlowDto reverseFlow = new FlowDto(forwardFlow);
        reverseFlow.setSourceSwitch(forwardFlow.getDestinationSwitch());
        reverseFlow.setSourcePort(forwardFlow.getDestinationPort());
        reverseFlow.setSourceVlan(forwardFlow.getDestinationVlan());
        reverseFlow.setDestinationSwitch(forwardFlow.getSourceSwitch());
        reverseFlow.setDestinationPort(forwardFlow.getSourcePort());
        reverseFlow.setDestinationVlan(forwardFlow.getSourceVlan());

        flows.put(flowId, new FlowPairDto<>(forwardFlow, reverseFlow));
    }

    /**
     * Get a stub for {@link TraffExamService}. The instance is tied to the factory state.
     */
    public TraffExamService getTraffExamStub() {
        TraffExamService serviceMock = mock(TraffExamService.class);

        when(serviceMock.hostByName(any()))
                .thenAnswer(invocation -> {
                    String hostName = (String) invocation.getArguments()[0];
                    Host host = mock(Host.class);
                    when(host.getName()).thenReturn(hostName);
                    return host;
                });

        when(serviceMock.waitExam(any()))
                .thenAnswer(invocation -> {
                    Exam exam = (Exam) invocation.getArguments()[0];
                    ExamReport report = mock(ExamReport.class);
                    when(report.hasError()).thenReturn(false);
                    when(report.hasTraffic()).thenReturn(flows.containsKey(exam.getFlow().getId()));
                    when(report.getBandwidth()).thenReturn(new Bandwidth(exam.getFlow().getMaximumBandwidth()));
                    return report;
                });

        return serviceMock;
    }
}
