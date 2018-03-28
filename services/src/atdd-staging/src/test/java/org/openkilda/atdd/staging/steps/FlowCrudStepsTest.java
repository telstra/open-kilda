package org.openkilda.atdd.staging.steps;

import static java.util.Collections.singletonList;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasProperty;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
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
import java.util.Set;
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

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

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
        final Set<FlowPayload> flows = flowCrudSteps.flows;
        assertEquals(1, flows.size());
        assertThat(flows, hasItem(allOf(
                hasProperty("source", allOf(
                        hasProperty("portId", equalTo(10)),
                        hasProperty("vlanId", equalTo(1)))),
                hasProperty("destination", allOf(
                        hasProperty("portId", equalTo(10)),
                        hasProperty("vlanId", equalTo(1))))
        )));
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
        final Set<FlowPayload> flows = flowCrudSteps.flows;
        assertEquals(2, flows.size());

        assertThat(flows, hasItem(allOf(
                hasProperty("source", allOf(
                        hasProperty("portId", equalTo(10)),
                        hasProperty("vlanId", equalTo(1)))),
                hasProperty("destination", allOf(
                        hasProperty("portId", equalTo(10)),
                        hasProperty("vlanId", equalTo(1))))
        )));

        assertThat(flows, hasItem(allOf(
                hasProperty("source", allOf(
                        hasProperty("portId", equalTo(10)),
                        hasProperty("vlanId", equalTo(2)))),
                hasProperty("destination", allOf(
                        hasProperty("portId", equalTo(10)),
                        hasProperty("vlanId", equalTo(2))))
        )));
    }

    @Test
    public void shouldSkipFlowsOverTheSameSwitches() {
        // given
        when(topologyEngineService.getPaths(eq("00:00:00:00:00:01"), eq("00:00:00:00:00:01")))
                .thenReturn(singletonList(new PathInfoData()));

        // when
        flowCrudSteps.defineFlowsOverAllSwitches();

        // then
        final Set<FlowPayload> flows = flowCrudSteps.flows;
        assertEquals(0, flows.size());
    }

    @Test
    public void shouldDefineFlowCrossVlan() {
        // given
        when(topologyEngineService.getPaths(eq("00:00:00:00:00:01"), eq("00:00:00:00:00:04")))
                .thenReturn(singletonList(new PathInfoData()));

        // when
        flowCrudSteps.defineFlowsOverAllSwitches();

        // then
        final Set<FlowPayload> flows = flowCrudSteps.flows;
        assertEquals(1, flows.size());

        assertThat(flows, hasItem(allOf(
                hasProperty("source", allOf(
                        hasProperty("portId", equalTo(10)),
                        hasProperty("vlanId", equalTo(1)))),
                hasProperty("destination", allOf(
                        hasProperty("portId", equalTo(10)),
                        hasProperty("vlanId", equalTo(50))))
        )));
    }

    @Test
    public void failIfNoVlanAvailable() {
        // given
        when(topologyEngineService.getPaths(eq("00:00:00:00:00:04"), eq("00:00:00:00:00:02")))
                .thenReturn(singletonList(new PathInfoData()));
        when(topologyEngineService.getPaths(eq("00:00:00:00:00:04"), eq("00:00:00:00:00:01")))
                .thenReturn(singletonList(new PathInfoData()));

        expectedException.expect(IllegalStateException.class);

        // when
        flowCrudSteps.defineFlowsOverAllSwitches();

        // then
        fail();
    }
}