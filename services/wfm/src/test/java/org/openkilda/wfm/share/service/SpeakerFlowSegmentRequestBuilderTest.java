/* Copyright 2019 Telstra Open Source
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

package org.openkilda.wfm.share.service;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

import org.openkilda.floodlight.api.request.EgressFlowSegmentBlankRequest;
import org.openkilda.floodlight.api.request.FlowSegmentBlankGenericResolver;
import org.openkilda.floodlight.api.request.FlowSegmentRequest;
import org.openkilda.floodlight.api.request.IngressFlowSegmentBlankRequest;
import org.openkilda.floodlight.api.request.TransitFlowSegmentBlankRequest;
import org.openkilda.model.Cookie;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.MeterConfig;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.TransitVlan;
import org.openkilda.persistence.repositories.TransitVlanRepository;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.Neo4jBasedTest;
import org.openkilda.wfm.share.flow.resources.FlowResourcesConfig;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;

import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;

public class SpeakerFlowSegmentRequestBuilderTest extends Neo4jBasedTest {
    private static final CommandContext COMMAND_CONTEXT = new CommandContext();
    private static final SwitchId SWITCH_1 = new SwitchId("00:00:00:00:00:00:00:01");
    private static final SwitchId SWITCH_2 = new SwitchId("00:00:00:00:00:00:00:02");
    private static final SwitchId SWITCH_3 = new SwitchId("00:00:00:00:00:00:00:03");
    private static final Random UNSEED_RANDOM = new Random();

    private SpeakerFlowSegmentRequestBuilder target;
    private TransitVlanRepository vlanRepository;

    @Before
    public void setUp() {
        FlowResourcesManager resourcesManager = new FlowResourcesManager(persistenceManager,
                configurationProvider.getConfiguration(FlowResourcesConfig.class));
        target = new SpeakerFlowSegmentRequestBuilder(resourcesManager);
        vlanRepository = persistenceManager.getRepositoryFactory().createTransitVlanRepository();
    }

    @Test
    public void shouldCreateNonIngressRequestsWithoutVlans() {
        Switch srcSwitch = Switch.builder().switchId(SWITCH_1).build();
        Switch destSwitch = Switch.builder().switchId(SWITCH_2).build();

        Flow flow = buildFlow(srcSwitch, 1, 0, destSwitch, 2, 0, 0);
        setSegmentsWithoutTransitSwitches(
                Objects.requireNonNull(flow.getForwardPath()), Objects.requireNonNull(flow.getReversePath()));

        List<FlowSegmentBlankGenericResolver> commands = target.buildAllExceptIngress(COMMAND_CONTEXT, flow);
        assertEquals(2, commands.size());

        verifyForwardEgressRequest(flow, commands.get(0).makeInstallRequest());
        verifyReverseEgressRequest(flow, commands.get(1).makeInstallRequest());
    }

    @Test
    public void shouldCreateNonIngressCommandsWithTransitSwitch() {
        Switch srcSwitch = Switch.builder().switchId(SWITCH_1).build();
        Switch destSwitch = Switch.builder().switchId(SWITCH_3).build();

        Flow flow = buildFlow(srcSwitch, 1, 101, destSwitch, 2, 102, 0);
        setSegmentsWithTransitSwitches(
                Objects.requireNonNull(flow.getForwardPath()), Objects.requireNonNull(flow.getReversePath()));

        List<FlowSegmentBlankGenericResolver> commands = target.buildAllExceptIngress(COMMAND_CONTEXT, flow);
        assertEquals(4, commands.size());

        verifyForwardTransitRequest(flow, SWITCH_2, commands.get(0).makeInstallRequest());
        verifyForwardEgressRequest(flow, commands.get(1).makeInstallRequest());

        verifyReverseTransitRequest(flow, SWITCH_2, commands.get(2).makeInstallRequest());
        verifyReverseEgressRequest(flow, commands.get(3).makeInstallRequest());
    }

    @Test
    public void shouldCreateIngressWithoutMeterCommands() {
        commonIngressCommandTest(0);
    }

    @Test
    public void shouldCreateIngressCommands() {
        commonIngressCommandTest(1000);
    }

    private void commonIngressCommandTest(int bandwidth) {
        Switch srcSwitch = Switch.builder().switchId(SWITCH_1).build();
        Switch destSwitch = Switch.builder().switchId(SWITCH_2).build();

        Flow flow = buildFlow(srcSwitch, 1, 101, destSwitch, 2, 102, bandwidth);
        setSegmentsWithoutTransitSwitches(
                Objects.requireNonNull(flow.getForwardPath()), Objects.requireNonNull(flow.getReversePath()));

        List<FlowSegmentBlankGenericResolver> commands = target.buildIngressOnly(COMMAND_CONTEXT, flow);
        assertEquals(2, commands.size());

        verifyForwardIngressRequest(flow, commands.get(0).makeInstallRequest());
        verifyReverseIngressRequest(flow, commands.get(1).makeInstallRequest());
    }

    private IngressFlowSegmentBlankRequest verifyForwardIngressRequest(Flow flow, FlowSegmentRequest rawRequest) {
        FlowPath path = Objects.requireNonNull(flow.getForwardPath());
        IngressFlowSegmentBlankRequest request = verifyCommonIngressRequest(flow, path, rawRequest);

        assertEquals(flow.getSrcSwitch().getSwitchId(), request.getSwitchId());
        FlowEndpoint endpoint = new FlowEndpoint(
                flow.getSrcSwitch().getSwitchId(), flow.getSrcPort(), flow.getSrcVlan());
        assertEquals(endpoint, request.getEndpoint());

        return request;
    }

    private IngressFlowSegmentBlankRequest verifyReverseIngressRequest(Flow flow, FlowSegmentRequest rawRequest) {
        FlowPath path = Objects.requireNonNull(flow.getReversePath());
        IngressFlowSegmentBlankRequest request = verifyCommonIngressRequest(flow, path, rawRequest);

        assertEquals(flow.getDestSwitch().getSwitchId(), request.getSwitchId());
        FlowEndpoint endpoint = new FlowEndpoint(
                flow.getDestSwitch().getSwitchId(), flow.getDestPort(), flow.getDestVlan());
        assertEquals(endpoint, request.getEndpoint());

        return request;
    }

    private IngressFlowSegmentBlankRequest verifyCommonIngressRequest(
            Flow flow, FlowPath path, FlowSegmentRequest rawRequest) {
        assertThat(
                "Should be egress segment request", rawRequest, instanceOf(IngressFlowSegmentBlankRequest.class));
        IngressFlowSegmentBlankRequest request = (IngressFlowSegmentBlankRequest) rawRequest;

        assertEquals(flow.getFlowId(), request.getFlowId());
        assertEquals(path.getCookie(), request.getCookie());
        assertEquals(path.getSegments().get(0).getSrcPort(), (int) request.getIslPort());

        if (0 < flow.getBandwidth()) {
            MeterConfig config = new MeterConfig(path.getMeterId(), flow.getBandwidth());
            assertEquals(config, request.getMeterConfig());
        } else {
            assertNull(request.getMeterConfig());
        }

        verifyVlanEncapsulation(flow, path, request.getEncapsulation());

        return request;
    }

    private void verifyForwardTransitRequest(Flow flow, SwitchId datapath, FlowSegmentRequest rawRequest) {
        FlowPath path = Objects.requireNonNull(flow.getForwardPath());
        verifyCommonTransitRequest(flow, path, datapath, rawRequest);
    }

    private void verifyReverseTransitRequest(Flow flow, SwitchId datapath, FlowSegmentRequest rawRequest) {
        FlowPath path = Objects.requireNonNull(flow.getReversePath());
        verifyCommonTransitRequest(flow, path, datapath, rawRequest);
    }

    private TransitFlowSegmentBlankRequest verifyCommonTransitRequest(
            Flow flow, FlowPath path, SwitchId datapath, FlowSegmentRequest rawRequest) {
        assertThat(
                "Should be egress segment request", rawRequest, instanceOf(TransitFlowSegmentBlankRequest.class));
        TransitFlowSegmentBlankRequest request = (TransitFlowSegmentBlankRequest) rawRequest;

        assertEquals(flow.getFlowId(), request.getFlowId());
        assertEquals(path.getCookie(), request.getCookie());
        assertEquals(SWITCH_2, request.getSwitchId());

        PathSegment ingress = null;
        PathSegment egress = null;
        for (PathSegment segment : path.getSegments()) {
            if (datapath.equals(segment.getDestSwitch().getSwitchId())) {
                ingress = segment;
            } else if (datapath.equals(segment.getSrcSwitch().getSwitchId())) {
                egress = segment;
            }
        }

        assertNotNull(ingress);
        assertNotNull(egress);

        assertEquals(ingress.getDestPort(), (int) request.getIngressIslPort());
        assertEquals(egress.getSrcPort(), (int) request.getEgressIslPort());

        verifyVlanEncapsulation(flow, path, request.getEncapsulation());
        return request;
    }

    private void verifyForwardEgressRequest(Flow flow, FlowSegmentRequest rawRequest) {
        FlowPath path = Objects.requireNonNull(flow.getForwardPath());
        EgressFlowSegmentBlankRequest request = verifyCommonEgressRequest(flow, path, rawRequest);

        FlowEndpoint expectedEndpoint = new FlowEndpoint(
                flow.getDestSwitch().getSwitchId(), flow.getDestPort(), flow.getDestVlan());
        assertEquals(expectedEndpoint, request.getEndpoint());

        FlowEndpoint expectedIngressEndpoint = new FlowEndpoint(
                flow.getSrcSwitch().getSwitchId(), flow.getSrcPort(), flow.getSrcVlan());
        assertEquals(expectedIngressEndpoint, request.getIngressEndpoint());
    }

    private void verifyReverseEgressRequest(Flow flow, FlowSegmentRequest rawRequest) {
        FlowPath path = Objects.requireNonNull(flow.getReversePath());
        EgressFlowSegmentBlankRequest request = verifyCommonEgressRequest(flow, path, rawRequest);

        FlowEndpoint expectedEndpoint = new FlowEndpoint(
                flow.getSrcSwitch().getSwitchId(), flow.getSrcPort(), flow.getSrcVlan());
        assertEquals(expectedEndpoint, request.getEndpoint());

        FlowEndpoint expectedIngressEndpoint = new FlowEndpoint(
                flow.getDestSwitch().getSwitchId(), flow.getDestPort(), flow.getDestVlan());
        assertEquals(expectedIngressEndpoint, request.getIngressEndpoint());
    }

    private EgressFlowSegmentBlankRequest verifyCommonEgressRequest(
            Flow flow, FlowPath path, FlowSegmentRequest rawRequest) {
        assertThat(
                "Should be egress segment request", rawRequest, instanceOf(EgressFlowSegmentBlankRequest.class));
        EgressFlowSegmentBlankRequest request = (EgressFlowSegmentBlankRequest) rawRequest;

        assertEquals(flow.getFlowId(), request.getFlowId());
        assertEquals(path.getDestSwitch().getSwitchId(), request.getSwitchId());
        assertEquals(path.getCookie(), request.getCookie());
        assertEquals(path.getSegments().get(path.getSegments().size() - 1).getDestPort(), (int) request.getIslPort());

        verifyVlanEncapsulation(flow, path, request.getEncapsulation());

        return request;
    }

    private void verifyVlanEncapsulation(Flow flow, FlowPath path, FlowTransitEncapsulation encapsulation) {
        assertEquals(FlowEncapsulationType.TRANSIT_VLAN, encapsulation.getType());
        TransitVlan transitVlan = vlanRepository.findByPathId(path.getPathId(),
                                                              flow.getOppositePathId(path.getPathId()))
                .stream().findAny()
                .orElseThrow(() -> new IllegalStateException("Vlan should be present"));
        assertEquals(transitVlan.getVlan(), (int) encapsulation.getId());
    }

    private void setSegmentsWithoutTransitSwitches(FlowPath forward, FlowPath reverse) {
        PathSegment switch1ToSwitch2 = PathSegment.builder()
                .srcSwitch(forward.getSrcSwitch())
                .srcPort(12)
                .destSwitch(forward.getDestSwitch())
                .destPort(22)
                .build();
        forward.setSegments(ImmutableList.of(switch1ToSwitch2));
        PathSegment switch2ToSwitch1 = PathSegment.builder()
                .srcSwitch(reverse.getSrcSwitch())
                .srcPort(22)
                .destSwitch(reverse.getDestSwitch())
                .destPort(12)
                .build();
        reverse.setSegments(ImmutableList.of(switch2ToSwitch1));
    }

    private void setSegmentsWithTransitSwitches(FlowPath forward, FlowPath reverse) {
        PathSegment switch1ToSwitch2 = PathSegment.builder()
                .srcSwitch(forward.getSrcSwitch())
                .srcPort(12)
                .destSwitch(Switch.builder().switchId(SWITCH_2).build())
                .destPort(21)
                .build();
        PathSegment switch2ToSwitch3 = PathSegment.builder()
                .srcSwitch(Switch.builder().switchId(SWITCH_2).build())
                .srcPort(23)
                .destSwitch(forward.getDestSwitch())
                .destPort(32)
                .build();
        forward.setSegments(ImmutableList.of(switch1ToSwitch2, switch2ToSwitch3));

        PathSegment switch3ToSwitch2 = PathSegment.builder()
                .srcSwitch(reverse.getSrcSwitch())
                .srcPort(32)
                .destSwitch(Switch.builder().switchId(SWITCH_2).build())
                .destPort(23)
                .build();
        PathSegment switch2ToSwitch1 = PathSegment.builder()
                .srcSwitch(Switch.builder().switchId(SWITCH_2).build())
                .srcPort(21)
                .destSwitch(reverse.getDestSwitch())
                .destPort(12)
                .build();
        reverse.setSegments(ImmutableList.of(switch3ToSwitch2, switch2ToSwitch1));
    }

    private FlowPath buildFlowPath(Flow flow, Switch srcSwitch, Switch dstSwitch, long bandwidth) {
        PathId forwardPathId = new PathId(UUID.randomUUID().toString());
        TransitVlan forwardVlan = TransitVlan.builder()
                .flowId(flow.getFlowId())
                .pathId(forwardPathId)
                .vlan(UNSEED_RANDOM.nextInt())
                .build();
        vlanRepository.createOrUpdate(forwardVlan);
        return FlowPath.builder()
                .flow(flow)
                .bandwidth(bandwidth)
                .cookie(new Cookie(UNSEED_RANDOM.nextLong()))
                .meterId(bandwidth != 0 ? new MeterId(UNSEED_RANDOM.nextInt()) : null)
                .srcSwitch(srcSwitch)
                .destSwitch(dstSwitch)
                .pathId(forwardPathId)
                .build();
    }

    private Flow buildFlow(Switch srcSwitch, int srcPort, int srcVlan, Switch dstSwitch, int dstPort, int dstVlan,
                           int bandwidth) {
        Flow flow = Flow.builder()
                .flowId(UUID.randomUUID().toString())
                .srcSwitch(srcSwitch)
                .srcPort(srcPort)
                .srcVlan(srcVlan)
                .destSwitch(dstSwitch)
                .destPort(dstPort)
                .destVlan(dstVlan)
                .bandwidth(bandwidth)
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .build();

        flow.setForwardPath(buildFlowPath(flow, flow.getSrcSwitch(), flow.getDestSwitch(), flow.getBandwidth()));
        flow.setReversePath(buildFlowPath(flow, flow.getDestSwitch(), flow.getSrcSwitch(), flow.getBandwidth()));

        return flow;
    }
}
