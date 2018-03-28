package org.openkilda.atdd.staging.tests.flow_crud.all_switches;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import cucumber.api.CucumberOptions;
import cucumber.api.java.After;
import cucumber.api.java.Before;
import org.apache.commons.lang3.SerializationUtils;
import org.junit.runner.RunWith;
import org.mockito.stubbing.Answer;
import org.openkilda.atdd.staging.cucumber.CucumberWithSpringProfile;
import org.openkilda.atdd.staging.model.topology.TopologyDefinition;
import org.openkilda.atdd.staging.model.topology.TopologyDefinition.Switch;
import org.openkilda.atdd.staging.service.floodlight.FloodlightService;
import org.openkilda.atdd.staging.service.northbound.NorthboundService;
import org.openkilda.atdd.staging.service.topology.TopologyEngineService;
import org.openkilda.atdd.staging.service.traffexam.OperationalException;
import org.openkilda.atdd.staging.service.traffexam.TraffExamService;
import org.openkilda.atdd.staging.service.traffexam.model.Host;
import org.openkilda.messaging.info.event.IslChangeType;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.info.event.SwitchState;
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.model.ImmutablePair;
import org.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.messaging.payload.flow.FlowState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;


@RunWith(CucumberWithSpringProfile.class)
@CucumberOptions(features = {"classpath:features/flow_crud.feature"},
        glue = {"org.openkilda.atdd.staging.tests.flow_crud.all_switches", "org.openkilda.atdd.staging.steps"},
        name = {"Create, read, update and delete flows across the entire set of switches"})
@ActiveProfiles("mock")
public class FlowCrudOverAllSwitchesTest {

    public static class DiscoveryMechanismHook {

        @Autowired
        private NorthboundService northboundService;

        @Autowired
        private FloodlightService floodlightService;

        @Autowired
        private TopologyEngineService topologyEngineService;

        @Autowired
        private TraffExamService traffExamService;

        @Autowired
        private TopologyDefinition topologyDefinition;

        final Set<String> removedFlows = new HashSet<>();

        @Before
        public void prepareMocks() throws IOException {
            setup5SwitchTopology();

            mockFlowInTE("sw1", 10, "sw2", 10, 1);
            mockFlowInTE("sw1", 10, "sw3", 10, 2);

            mockFlowCrudInNorthbound();

            mockTraffExam();

            removedFlows.clear();
        }

        private void setup5SwitchTopology() throws IOException {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            mapper.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);
            TopologyDefinition topology = mapper.readValue(
                    getClass().getResourceAsStream("/5-switch-test-topology.yaml"), TopologyDefinition.class);

            when(topologyDefinition.getActiveSwitches()).thenReturn(topology.getActiveSwitches());
            List<SwitchInfoData> discoveredSwitches = topology.getActiveSwitches().stream()
                    .map(sw -> new SwitchInfoData(sw.getDpId(), SwitchState.ACTIVATED, "", "", "", ""))
                    .collect(toList());
            when(topologyEngineService.getActiveSwitches()).thenReturn(discoveredSwitches);

            when(topologyDefinition.getIslsForActiveSwitches()).thenReturn(topology.getIslsForActiveSwitches());
            List<IslInfoData> discoveredLinks = topology.getIslsForActiveSwitches().stream()
                    .flatMap(link -> Stream.of(
                            new IslInfoData(0,
                                    asList(new PathNode(link.getSrcSwitch().getDpId(), link.getSrcPort(), 0),
                                            new PathNode(link.getDstSwitch().getDpId(), link.getDstPort(), 1)),
                                    link.getMaxBandwidth(), IslChangeType.DISCOVERED, 0),
                            new IslInfoData(0,
                                    asList(new PathNode(link.getDstSwitch().getDpId(), link.getDstPort(), 0),
                                            new PathNode(link.getSrcSwitch().getDpId(), link.getSrcPort(), 1)),
                                    link.getMaxBandwidth(), IslChangeType.DISCOVERED, 0)
                    ))
                    .collect(toList());
            when(topologyEngineService.getActiveLinks()).thenReturn(discoveredLinks);

            when(topologyDefinition.getActiveTrafgens()).thenReturn(topology.getActiveTrafgens());
        }

        private void mockFlowInTE(String srcSwitchName, int srcPort, String destSwitchName, int destPort, int vlan) {
            List<Switch> switchDefs = topologyDefinition.getActiveSwitches();
            Switch srcSwitch = switchDefs.stream()
                    .filter(sw -> sw.getName().equals(srcSwitchName))
                    .findFirst().get();
            Switch destSwitch = switchDefs.stream()
                    .filter(sw -> sw.getName().equals(destSwitchName))
                    .findFirst().get();

            when(topologyEngineService.getPaths(eq(srcSwitch.getDpId()), eq(destSwitch.getDpId())))
                    .thenReturn(singletonList(new PathInfoData()));

            String flowDesc = format("%s-%s", srcSwitchName, destSwitchName);
            when(topologyEngineService.getFlow(startsWith(flowDesc)))
                    .then((Answer<ImmutablePair<Flow, Flow>>) invocation -> {
                        String flowId = (String) invocation.getArguments()[0];
                        if (!removedFlows.contains(flowId)) {
                            return new ImmutablePair<>(new Flow(flowId,
                                    10000, false, 0, flowDesc, null,
                                    srcSwitch.getDpId(), destSwitch.getDpId(), srcPort, destPort,
                                    vlan, vlan, 0, 0, null, null), null);
                        }
                        return null;
                    });
        }

        private void mockFlowCrudInNorthbound() {
            when(northboundService.addFlow(any()))
                    .then((Answer<FlowPayload>) invocation -> {
                        FlowPayload result = SerializationUtils.clone(((FlowPayload) invocation.getArguments()[0]));
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
            when(northboundService.updateFlow(any(), any())).thenReturn(mock(FlowPayload.class));
            when(northboundService.deleteFlow(any()))
                    .then((Answer<FlowPayload>) invocation -> {
                        removedFlows.add((String) invocation.getArguments()[0]);
                        return mock(FlowPayload.class);
                    });
        }

        private void mockTraffExam() {
            when(traffExamService.hostByName(any()))
                    .then((Answer<Host>) invocation -> {
                        String hostName = (String) invocation.getArguments()[0];
                        Host host = mock(Host.class);
                        when(host.getName()).thenReturn(hostName);
                        return host;
                    });
        }

        @After
        public void assertsAndVerifyMocks() throws OperationalException {
            verify(northboundService, times(2)).addFlow(any());
            verify(northboundService, times(2)).updateFlow(any(), any());
            verify(northboundService, times(2)).deleteFlow(any());

            // 2 flows * 2 directions * (on create + on update) = 8 times
            verify(traffExamService, times(8)).startExam(any());
            // 2 flows * (on create + on update) = 4 times
            verify(traffExamService, times(4)).waitExam(any());

            assertEquals(2, removedFlows.size());
        }
    }
}
