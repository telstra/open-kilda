package org.openkilda.atdd.staging.steps;

import static java.util.Collections.singletonList;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasProperty;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.openkilda.atdd.staging.model.topology.TopologyDefinition;
import org.openkilda.atdd.staging.service.floodlight.FloodlightService;
import org.openkilda.atdd.staging.service.northbound.NorthboundService;
import org.openkilda.atdd.staging.service.topology.TopologyEngineService;
import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.messaging.payload.flow.FlowPayload;

import java.io.IOException;
import javax.annotation.Resource;

public class FlowCrudStepsTest {

    @Mock
    private NorthboundService northboundService;

    @Mock
    private FloodlightService floodlightService;

    @Mock
    private TopologyEngineService topologyEngineService;

    @Mock
    private TopologyDefinition topologyDefinition;

    @InjectMocks
    @Resource
    private FlowCrudSteps flowCrudSteps;

    @Before
    public void setUp() throws IOException {
        MockitoAnnotations.initMocks(this);

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);
        TopologyDefinition topology = mapper.readValue(
                getClass().getResourceAsStream("/5-switch-test-topology.yaml"), TopologyDefinition.class);

        when(topologyDefinition.getActiveSwitches()).thenReturn(topology.getActiveSwitches());
    }

    @Test
    public void shouldDefineFlowsOver2Switches() {
        // given
        when(topologyEngineService.getPaths(eq("00:00:00:00:00:01"), eq("00:00:00:00:00:02")))
                .thenReturn(singletonList(new PathInfoData()));

        // when
        flowCrudSteps.defineFlowsOverAllSwitches();

        // then
        assertEquals(1, flowCrudSteps.flows.size());
        final FlowPayload flowPayload = flowCrudSteps.flows.get(0);
        assertEquals(20, (int) flowPayload.getSource().getPortId());
        assertEquals(1, (int) flowPayload.getSource().getVlanId());
        assertEquals(20, (int) flowPayload.getDestination().getPortId());
        assertEquals(1, (int) flowPayload.getDestination().getVlanId());
    }

    @Test
    public void shouldDefineFlowsOver3Switches() {
        // given
        when(topologyEngineService.getPaths(eq("00:00:00:00:00:01"), eq("00:00:00:00:00:02")))
                .thenReturn(singletonList(new PathInfoData()));
        when(topologyEngineService.getPaths(eq("00:00:00:00:00:02"), eq("00:00:00:00:00:03")))
                .thenReturn(singletonList(new PathInfoData()));

        // when
        flowCrudSteps.defineFlowsOverAllSwitches();

        // then
        assertEquals(2, flowCrudSteps.flows.size());

        final FlowPayload sw1sw2Flow = flowCrudSteps.flows.get(0);
        assertEquals(20, (int) sw1sw2Flow.getSource().getPortId());
        assertEquals(1, (int) sw1sw2Flow.getSource().getVlanId());
        assertEquals(20, (int) sw1sw2Flow.getDestination().getPortId());
        assertEquals(1, (int) sw1sw2Flow.getDestination().getVlanId());

        final FlowPayload sw2sw3Flow = flowCrudSteps.flows.get(1);
        assertEquals(20, (int) sw2sw3Flow.getSource().getPortId());
        assertEquals(2, (int) sw2sw3Flow.getSource().getVlanId());
        assertEquals(20, (int) sw2sw3Flow.getDestination().getPortId());
        assertEquals(2, (int) sw2sw3Flow.getDestination().getVlanId());
    }

    @Test
    public void shouldDefineFlowsOverTheSameSwitches() {
        // given
        when(topologyEngineService.getPaths(eq("00:00:00:00:00:01"), eq("00:00:00:00:00:01")))
                .thenReturn(singletonList(new PathInfoData()));

        // when
        flowCrudSteps.defineFlowsOverAllSwitches();

        // then
        assertEquals(1, flowCrudSteps.flows.size());
        final FlowPayload flowPayload = flowCrudSteps.flows.get(0);
        assertEquals(20, (int) flowPayload.getSource().getPortId());
        assertEquals(1, (int) flowPayload.getSource().getVlanId());
        assertEquals(21, (int) flowPayload.getDestination().getPortId());
        assertEquals(1, (int) flowPayload.getDestination().getVlanId());
    }

    @Test
    public void shouldDefineFlowCrossVlan() {
        // given
        when(topologyEngineService.getPaths(eq("00:00:00:00:00:01"), eq("00:00:00:00:00:04")))
                .thenReturn(singletonList(new PathInfoData()));

        // when
        flowCrudSteps.defineFlowsOverAllSwitches();

        // then
        assertEquals(1, flowCrudSteps.flows.size());
        final FlowPayload flowPayload = flowCrudSteps.flows.get(0);
        assertEquals(20, (int) flowPayload.getSource().getPortId());
        assertEquals(1, (int) flowPayload.getSource().getVlanId());
        assertEquals(20, (int) flowPayload.getDestination().getPortId());
        assertEquals(50, (int) flowPayload.getDestination().getVlanId());
    }

    @Test
    public void failDefineFlowsWithPortConflict() {
        // given
        when(topologyEngineService.getPaths(eq("00:00:00:00:00:02"), eq("00:00:00:00:00:02")))
                .thenReturn(singletonList(new PathInfoData()));

        // when
        flowCrudSteps.defineFlowsOverAllSwitches();

        // then
        assertTrue(flowCrudSteps.flows.isEmpty());
    }

    @Test
    public void shouldSkipSwitchesIfNoVlanAvailable() {
        // given
        when(topologyEngineService.getPaths(eq("00:00:00:00:00:04"), eq("00:00:00:00:00:02")))
                .thenReturn(singletonList(new PathInfoData()));
        when(topologyEngineService.getPaths(eq("00:00:00:00:00:04"), eq("00:00:00:00:00:01")))
                .thenReturn(singletonList(new PathInfoData()));

        // when
        flowCrudSteps.defineFlowsOverAllSwitches();

        // then
        assertEquals(1, flowCrudSteps.flows.size());
        final FlowPayload flowPayload = flowCrudSteps.flows.get(0);
        assertThat(flowPayload.getSource(), hasProperty("switchId", equalTo("00:00:00:00:00:04")));
        assertThat(flowPayload.getDestination(), hasProperty("switchId", equalTo("00:00:00:00:00:01")));
    }
}