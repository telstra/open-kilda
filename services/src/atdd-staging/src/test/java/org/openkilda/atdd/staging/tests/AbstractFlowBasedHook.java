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
package org.openkilda.atdd.staging.tests;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.commons.lang3.SerializationUtils;
import org.mockito.stubbing.Answer;
import org.openkilda.atdd.staging.model.topology.TopologyDefinition;
import org.openkilda.atdd.staging.model.topology.TopologyDefinition.Switch;
import org.openkilda.atdd.staging.service.floodlight.FloodlightService;
import org.openkilda.atdd.staging.service.floodlight.model.MeterBand;
import org.openkilda.atdd.staging.service.floodlight.model.MeterEntry;
import org.openkilda.atdd.staging.service.floodlight.model.MetersEntriesMap;
import org.openkilda.atdd.staging.service.northbound.NorthboundService;
import org.openkilda.atdd.staging.service.topology.TopologyEngineService;
import org.openkilda.atdd.staging.service.traffexam.TraffExamService;
import org.openkilda.atdd.staging.service.traffexam.model.Host;
import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.model.ImmutablePair;
import org.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.messaging.payload.flow.FlowState;
import org.openkilda.northbound.dto.switches.RulesSyncResult;
import org.openkilda.northbound.dto.switches.RulesValidationResult;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public abstract class AbstractFlowBasedHook {

    @Autowired
    protected NorthboundService northboundService;

    @Autowired
    protected FloodlightService floodlightService;

    @Autowired
    protected TopologyEngineService topologyEngineService;

    @Autowired
    protected TraffExamService traffExamService;

    @Autowired
    protected TopologyDefinition topologyDefinition;

    protected final Set<String> removedFlows = new HashSet<>();

    protected void mockFlowInTE(String srcSwitchName, int srcPort, String destSwitchName, int destPort, int vlan, int meterId) {
        List<Switch> switchDefs = topologyDefinition.getActiveSwitches();
        Switch srcSwitch = switchDefs.stream()
                .filter(sw -> sw.getName().equals(srcSwitchName))
                .findFirst().get();
        Switch destSwitch = switchDefs.stream()
                .filter(sw -> sw.getName().equals(destSwitchName))
                .findFirst().get();

        when(topologyEngineService.getPaths(eq(srcSwitch.getDpId()), eq(destSwitch.getDpId())))
                .thenReturn(singletonList(new PathInfoData()));
        when(topologyEngineService.getPaths(eq(destSwitch.getDpId()), eq(srcSwitch.getDpId())))
                .thenReturn(singletonList(new PathInfoData()));

        String flowDesc = format("%s-%s", srcSwitchName, destSwitchName);
        when(topologyEngineService.getFlow(contains(flowDesc)))
                .then((Answer<ImmutablePair<Flow, Flow>>) invocation -> {
                    String flowId = (String) invocation.getArguments()[0];
                    if (!removedFlows.contains(flowId)) {
                        return new ImmutablePair<>(
                                new Flow(flowId,
                                        10000, false, 0, flowDesc, null,
                                        srcSwitch.getDpId(), destSwitch.getDpId(), srcPort, destPort,
                                        vlan, vlan, meterId, 0, null, null),
                                new Flow(flowId,
                                        10000, false, 0, flowDesc, null,
                                        destSwitch.getDpId(), srcSwitch.getDpId(), destPort, srcPort,
                                        vlan, vlan, meterId, 0, null, null)
                        );
                    }
                    return null;
                });

    }

    protected void mockMetersInFL(String switchName, int bandwidth, int... meterIds) {
        List<Switch> switchDefs = topologyDefinition.getActiveSwitches();
        String switchId = switchDefs.stream()
                .filter(sw -> sw.getName().equals(switchName))
                .findFirst()
                .map(Switch::getDpId).get();

        MetersEntriesMap meterEntries = new MetersEntriesMap();
        for(int meterId : meterIds) {
            MeterEntry entry = new MeterEntry(emptyList(), meterId,
                    singletonList(new MeterBand(bandwidth, 0, "", 1)), "");
            meterEntries.put(meterId, entry);
        }

        when(floodlightService.getMeters(eq(switchId)))
                .then((Answer<MetersEntriesMap>) invocation -> meterEntries);
    }

    protected void mockFlowCrudInNorthbound() {
        when(northboundService.getAllFlows())
                .then((Answer<List<FlowPayload>>) invocation ->
                        emptyList()
                );

        when(northboundService.synchronizeSwitchRules(any()))
                .then((Answer<RulesSyncResult>) invocation ->
                        new RulesSyncResult(emptyList(), emptyList(), emptyList(), emptyList())
                );

        when(northboundService.addFlow(any()))
                .then((Answer<FlowPayload>) invocation -> {
                    FlowPayload result = SerializationUtils.clone(((FlowPayload) invocation.getArguments()[0]));
                    result.setStatus(FlowState.ALLOCATED.toString());
                    result.setLastUpdated(LocalTime.now().toString());
                    return result;
                });
        when(northboundService.getFlowStatus(any()))
                .then((Answer<FlowIdStatusPayload>) invocation ->
                        new FlowIdStatusPayload((String) invocation.getArguments()[0], FlowState.UP));

        when(northboundService.getFlow(any()))
                .then((Answer<FlowPayload>) invocation -> {
                    String flowId = (String) invocation.getArguments()[0];
                    if (!removedFlows.contains(flowId)) {
                        FlowPayload result = mock(FlowPayload.class);
                        when(result.getId()).thenReturn(flowId);
                        return result;
                    }
                    return null;
                });
        when(northboundService.updateFlow(any(), any()))
                .then((Answer<FlowPayload>) invocation -> {
                    String flowId = (String) invocation.getArguments()[0];
                    removedFlows.add(flowId);
                    FlowPayload result = mock(FlowPayload.class);
                    when(result.getId()).thenReturn(flowId);
                    return result;
                });

        when(northboundService.deleteFlow(any()))
                .then((Answer<FlowPayload>) invocation -> {
                    String flowId = (String) invocation.getArguments()[0];
                    removedFlows.add(flowId);
                    FlowPayload result = mock(FlowPayload.class);
                    when(result.getId()).thenReturn(flowId);
                    return result;
                });

        when(northboundService.validateSwitchRules(any()))
                .then((Answer<RulesValidationResult>) invocation -> {
                    RulesValidationResult result = mock(RulesValidationResult.class);
                    when(result.getExcessRules()).thenReturn(emptyList());
                    when(result.getMissingRules()).thenReturn(emptyList());
                    return result;
                });
    }

    protected void mockTraffExam() {
        when(traffExamService.hostByName(any()))
                .then((Answer<Host>) invocation -> {
                    String hostName = (String) invocation.getArguments()[0];
                    Host host = mock(Host.class);
                    when(host.getName()).thenReturn(hostName);
                    return host;
                });
    }
}
