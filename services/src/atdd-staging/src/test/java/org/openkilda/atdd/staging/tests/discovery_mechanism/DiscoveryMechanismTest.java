package org.openkilda.atdd.staging.tests.discovery_mechanism;

import static java.util.Collections.emptyList;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import cucumber.api.CucumberOptions;
import cucumber.api.java.After;
import cucumber.api.java.Before;
import org.junit.runner.RunWith;
import org.openkilda.atdd.staging.cucumber.CucumberWithSpringProfile;
import org.openkilda.atdd.staging.service.floodlight.model.FlowApplyActions;
import org.openkilda.atdd.staging.service.floodlight.model.FlowEntriesMap;
import org.openkilda.atdd.staging.service.floodlight.model.FlowEntry;
import org.openkilda.atdd.staging.service.floodlight.model.FlowInstructions;
import org.openkilda.atdd.staging.service.floodlight.model.FlowMatchField;
import org.openkilda.atdd.staging.service.floodlight.model.SwitchEntry;
import org.openkilda.atdd.staging.model.topology.TopologyDefinition;
import org.openkilda.atdd.staging.model.topology.TopologyDefinition.Status;
import org.openkilda.atdd.staging.service.floodlight.FloodlightService;
import org.openkilda.atdd.staging.service.topology.TopologyEngineService;
import org.openkilda.messaging.info.event.IslChangeType;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.info.event.SwitchState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;


@RunWith(CucumberWithSpringProfile.class)
@CucumberOptions(features = {"classpath:features/discovery_mechanism.feature"},
        glue = {"org.openkilda.atdd.staging.tests.discovery_mechanism", "org.openkilda.atdd.staging.steps"})
@ActiveProfiles("mock")
public class DiscoveryMechanismTest {

    public static class DiscoveryMechanismHook {
        @Autowired
        private FloodlightService floodlightService;

        @Autowired
        private TopologyEngineService topologyEngineService;

        @Autowired
        private TopologyDefinition topologyDefinition;

        private final List<SwitchInfoData> discoveredSwitches = ImmutableList.of(
                new SwitchInfoData("00:00:00:00:00:01", SwitchState.ACTIVATED, EMPTY, EMPTY, EMPTY, EMPTY),
                new SwitchInfoData("00:00:00:00:00:02", SwitchState.ACTIVATED, EMPTY, EMPTY, EMPTY, EMPTY)
        );

        private final List<PathNode> path = ImmutableList.of(
                new PathNode(discoveredSwitches.get(0).getSwitchId(),0, 0),
                new PathNode(discoveredSwitches.get(1).getSwitchId(),0, 0)
        );

        private List<IslInfoData> discoveredLinks;

        @Before
        public void prepareMocks() {
            List<TopologyDefinition.Switch> switches = discoveredSwitches.stream()
                    .map(this::buildSwitchDefinition)
                    .collect(Collectors.toList());
            when(topologyDefinition.getActiveSwitches()).thenReturn(switches);

            List<TopologyDefinition.Isl> links = buildIslDefinitions(switches);
            when(topologyDefinition.getIslsForActiveSwitches()).thenReturn(links);

            //build forward and reverse isls as discovered links
            List<PathNode> reversedPath = new ArrayList<>(path);
            Collections.reverse(reversedPath);
            discoveredLinks = ImmutableList.of(
                    new IslInfoData(0L, path, 0L, IslChangeType.DISCOVERED, 0L),
                    new IslInfoData(0L, reversedPath, 0L, IslChangeType.DISCOVERED, 0L)
            );
            when(topologyEngineService.getActiveSwitches()).thenReturn(discoveredSwitches);
            when(topologyEngineService.getActiveLinks()).thenReturn(discoveredLinks);

            when(floodlightService.getSwitches()).thenReturn(buildFloodlightSwitches());
            when(floodlightService.getFlows(anyString())).thenAnswer(invocation -> {
                String switchId = (String)invocation.getArguments()[0];
                return buildFlows(switchId);
            });
        }

        @After
        public void verifyMocks() {
            verify(topologyEngineService).getActiveSwitches();
            verify(topologyEngineService).getActiveLinks();
            verify(topologyDefinition, times(3)).getActiveSwitches();
            verify(topologyDefinition).getIslsForActiveSwitches();
        }

        private TopologyDefinition.Switch buildSwitchDefinition(SwitchInfoData sw) {
            String version = getOFVersionForSwitch(sw.getSwitchId());
            return new TopologyDefinition.Switch(sw.getSwitchId(), sw.getSwitchId(), version,
                    Status.Active, emptyList());
        }

        private List<TopologyDefinition.Isl> buildIslDefinitions(List<TopologyDefinition.Switch> switches) {
            TopologyDefinition.Isl isl = new TopologyDefinition.Isl(
                    getSwitchById(path.get(0).getSwitchId(), switches), path.get(0).getPortNo(),
                    getSwitchById(path.get(1).getSwitchId(), switches), path.get(1).getPortNo(),
                    10000);
            return Collections.singletonList(isl);
        }

        private TopologyDefinition.Switch getSwitchById(String switchId, List<TopologyDefinition.Switch> switches) {
            return switches.stream()
                    .filter(sw -> switchId.equals(sw.getDpId()))
                    .findFirst()
                    .orElse(null);
        }

        private List<SwitchEntry> buildFloodlightSwitches() {
            List<SwitchEntry> switches = new ArrayList<>(2);
            SwitchEntry entry = SwitchEntry.builder()
                    .switchId(discoveredSwitches.get(0).getSwitchId())
                    .oFVersion("OF_12")
                    .build();
            switches.add(entry);

            entry = SwitchEntry.builder()
                    .switchId(discoveredSwitches.get(1).getSwitchId())
                    .oFVersion("OF_13")
                    .build();
            switches.add(entry);

            return switches;
        }

        private FlowEntriesMap buildFlows(String switchId) {
            FlowEntriesMap flowMap = new FlowEntriesMap();

            //broadcast verification flow (for all OF versions)
            String flowOutput = "controller";
            FlowApplyActions flowActions = FlowApplyActions.builder()
                    .flowOutput(flowOutput)
                    .field(switchId + "->eth_dst")
                    .build();

            FlowMatchField matchField = FlowMatchField.builder()
                    .ethDst("08:ed:02:ef:ff:ff")
                    .build();
            FlowInstructions flowInstructions = FlowInstructions.builder()
                    .applyActions(flowActions)
                    .build();
            FlowEntry flowEntry = FlowEntry.builder()
                    .instructions(flowInstructions)
                    .match(matchField)
                    .cookie("flow-0x8000000000000002")
                    .build();
            flowMap.put(flowEntry.getCookie(), flowEntry);

            //define drop flow
            FlowInstructions dropFlowInstructions = FlowInstructions.builder()
                    .none("drop").build();
            FlowEntry dropFlow = FlowEntry.builder()
                    .cookie("flow-0x8000000000000001")
                    .priority(1)
                    .instructions(dropFlowInstructions)
                    .build();
            flowMap.put(dropFlow.getCookie(), dropFlow);

            if ("OF_13".equals(getOFVersionForSwitch(switchId))) {
                //non-broadcast flow for versions 13 and later
                String flowFor13InstructionOutput = "controller";
                FlowApplyActions flowFor13Actions = FlowApplyActions.builder()
                        .flowOutput(flowFor13InstructionOutput)
                        .field(switchId + "->eth_dst")
                        .build();
                FlowInstructions flowFor13Instructions = FlowInstructions.builder()
                        .applyActions(flowFor13Actions)
                        .build();

                FlowMatchField matchField13Version = FlowMatchField.builder()
                        .ethDst(switchId)
                        .build();
                FlowEntry flowFor13Version = FlowEntry.builder()
                        .cookie("flow-0x8000000000000003")
                        .instructions(flowFor13Instructions)
                        .match(matchField13Version)
                        .build();

                flowMap.put(flowFor13Version.getCookie(), flowFor13Version);

            }

            return flowMap;
        }

        private String getOFVersionForSwitch(String switchId) {
            return "00:00:00:00:00:01".equals(switchId) ? "OF_12" : "OF_13";
        }
    }

}
