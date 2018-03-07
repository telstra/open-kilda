package org.openkilda.atdd.staging.tests.flow_crud.all_switches;

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
import org.openkilda.atdd.staging.service.floodlight.FloodlightService;
import org.openkilda.atdd.staging.service.northbound.NorthboundService;
import org.openkilda.atdd.staging.service.topology.TopologyEngineService;
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
        private TopologyDefinition topologyDefinition;

        final Set<String> removedFlows = new HashSet<>();

        @Before
        public void prepareMocks() throws IOException {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            mapper.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);
            TopologyDefinition topology = mapper.readValue(
                    getClass().getResourceAsStream("/3-switch-test-topology.yaml"), TopologyDefinition.class);

            when(topologyDefinition.getActiveSwitches()).thenReturn(topology.getActiveSwitches());

            List<SwitchInfoData> discoveredSwitches = topology.getActiveSwitches().stream()
                    .map(sw -> new SwitchInfoData(sw.getDpId(), SwitchState.ACTIVATED, "", "", "", ""))
                    .collect(toList());
            when(topologyEngineService.getActiveSwitches()).thenReturn(discoveredSwitches);

            List<IslInfoData> discoveredLinks = topology.getIslsForActiveSwitches().stream()
                    .map(link -> new IslInfoData(0,
                            singletonList(new PathNode(link.getSrcSwitch().getDpId(), link.getSrcPort(), 0)),
                            link.getMaxBandwidth(),
                            IslChangeType.DISCOVERED,
                            0))
                    .collect(toList());
            when(topologyEngineService.getAllLinks()).thenReturn(discoveredLinks);

            when(topologyEngineService.getPaths(eq("00:00:00:00:00:01"), eq("00:00:00:00:00:01")))
                    .thenReturn(singletonList(new PathInfoData()));
            when(topologyEngineService.getPaths(eq("00:00:00:00:00:01"), eq("00:00:00:00:00:02")))
                    .thenReturn(singletonList(new PathInfoData()));

            when(topologyEngineService.getFlow(startsWith("sw1-sw1")))
                    .then((Answer<ImmutablePair<Flow, Flow>>) invocation -> {
                        String flowId = (String) invocation.getArguments()[0];
                        if (!removedFlows.contains(flowId)) {
                            return new ImmutablePair<>(new Flow(flowId,
                                    10000, false, 0, flowId, null,
                                    "00:00:00:00:00:01", "00:00:00:00:00:01", 20, 21,
                                    1, 1, 0, 0, null, null), null);
                        }
                        return null;
                    });
            when(topologyEngineService.getFlow(startsWith("sw1-sw2")))
                    .then((Answer<ImmutablePair<Flow, Flow>>) invocation -> {
                        String flowId = (String) invocation.getArguments()[0];
                        if (!removedFlows.contains(flowId)) {
                            return new ImmutablePair<>(new Flow(flowId,
                                    10000, false, 0, flowId, null,
                                    "00:00:00:00:00:01", "00:00:00:00:00:02", 20, 20,
                                    2, 2, 0, 0, null, null), null);
                        }
                        return null;
                    });

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
                        if (!removedFlows.contains((String) invocation.getArguments()[0])) {
                            return mock(FlowPayload.class);
                        }
                        return null;
                    });
            when(northboundService.updateFlow(any(), any())).thenReturn(mock(FlowPayload.class));
            when(northboundService.deleteFlow(any()))
                    .then((Answer<FlowPayload>) invocation -> {
                        removedFlows.add((String) invocation.getArguments()[0]);
                        return mock(FlowPayload.class);
                    });

            removedFlows.clear();
        }

        @After
        public void assertsAndVerifyMocks() {
            verify(northboundService, times(2)).addFlow(any());
            verify(northboundService, times(2)).updateFlow(any(), any());
            verify(northboundService, times(2)).deleteFlow(any());

            assertEquals(2, removedFlows.size());
        }
    }
}
