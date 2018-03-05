package org.openkilda.atdd.staging.tests.discovery_mechanism;

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
import org.openkilda.atdd.staging.model.floodlight.FlowApplyActions;
import org.openkilda.atdd.staging.model.floodlight.FlowEntriesMap;
import org.openkilda.atdd.staging.model.floodlight.FlowEntry;
import org.openkilda.atdd.staging.model.floodlight.FlowInstructionOutput;
import org.openkilda.atdd.staging.model.floodlight.FlowInstructions;
import org.openkilda.atdd.staging.model.floodlight.SwitchEntry;
import org.openkilda.atdd.staging.model.topology.TopologyDefinition;
import org.openkilda.atdd.staging.model.topology.TopologyDefinition.Status;
import org.openkilda.atdd.staging.service.FloodlightService;
import org.openkilda.atdd.staging.service.TopologyEngineService;
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
                new SwitchInfoData("00:00:00:00:00:00:00:01", SwitchState.ACTIVATED, EMPTY, EMPTY, EMPTY, EMPTY),
                new SwitchInfoData("00:00:00:00:00:00:00:02", SwitchState.ACTIVATED, EMPTY, EMPTY, EMPTY, EMPTY)
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
            when(topologyEngineService.getAllLinks()).thenReturn(discoveredLinks);

            when(floodlightService.getSwitches()).thenReturn(buildFloodlightSwitches());
            when(floodlightService.getFlows(anyString())).thenAnswer(invocation -> {
                String switchId = (String)invocation.getArguments()[0];
                return buildFlows(switchId);
            });
        }

        @After
        public void verifyMocks() {
            verify(topologyEngineService).getActiveSwitches();
            verify(topologyEngineService).getAllLinks();
            verify(topologyDefinition, times(3)).getActiveSwitches();
            verify(topologyDefinition).getIslsForActiveSwitches();
        }

        private TopologyDefinition.Switch buildSwitchDefinition(SwitchInfoData sw) {
            String version = "00:00:00:00:00:00:00:01".equals(sw.getSwitchId()) ? "OF_12" : "OF_13";
            return new TopologyDefinition.Switch(sw.getSwitchId(), sw.getSwitchId(), version, Status.Active);
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
            SwitchEntry entry = new SwitchEntry();
            entry.setSwitchId(discoveredSwitches.get(0).getSwitchId());
            entry.setOFVersion("OF_12");
            switches.add(entry);

            entry = new SwitchEntry();
            entry.setSwitchId(discoveredSwitches.get(1).getSwitchId());
            entry.setOFVersion("OF_13");
            switches.add(entry);

            return switches;
        }

        private FlowEntriesMap buildFlows(String switchId) {
            FlowEntriesMap flowMap = new FlowEntriesMap();
            //define drop flow
            FlowEntry dropFlow = new FlowEntry();
            dropFlow.setCookie("flow-0x8000000000000001");
            dropFlow.setPriority(1);

            FlowInstructions dropFlowInstructions = new FlowInstructions();
            dropFlowInstructions.setNone("drop");
            dropFlow.setInstructions(dropFlowInstructions);
            flowMap.put(dropFlow.getCookie(), dropFlow);

            //common flow definition (for all OF versions)
            FlowEntry flowEntry = new FlowEntry();
            flowEntry.setCookie("flow-0x8000000000000002");

            FlowInstructions flowInstructions = new FlowInstructions();
            FlowApplyActions flowActions = new FlowApplyActions();
            FlowInstructionOutput flowInstructionOutput = new FlowInstructionOutput();
            flowInstructionOutput.setName("controller");
            flowInstructionOutput.setPortNumber(-3);
            flowInstructionOutput.setShortPortNumber(-3);
            flowInstructionOutput.setLength(4);
            flowActions.setFlowOutput(flowInstructionOutput);
            flowActions.setField(switchId + "->eth_dst");

            flowInstructions.setApplyActions(flowActions);
            flowEntry.setInstructions(flowInstructions);
            flowMap.put(flowEntry.getCookie(), flowEntry);

            //flow for OF versions 13 and later
            FlowEntry flowFor13Version = new FlowEntry();
            flowFor13Version.setCookie("flow-0x8000000000000003");

            FlowInstructions flowFor13Instructions = new FlowInstructions();
            FlowApplyActions flowFor13Actions = new FlowApplyActions();
            FlowInstructionOutput flowFor13InstructionOutput = new FlowInstructionOutput();
            flowFor13InstructionOutput.setName("controller");
            flowFor13InstructionOutput.setPortNumber(-3);
            flowFor13InstructionOutput.setShortPortNumber(-3);
            flowFor13InstructionOutput.setLength(4);
            flowFor13Actions.setFlowOutput(flowFor13InstructionOutput);
            flowFor13Actions.setField(switchId + "->eth_dst");

            flowFor13Instructions.setApplyActions(flowFor13Actions);
            flowFor13Version.setInstructions(flowFor13Instructions);
            flowMap.put(flowFor13Version.getCookie(), flowFor13Version);

            return flowMap;
        }
    }

}
