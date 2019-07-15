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

import static java.lang.String.format;

import org.openkilda.adapter.FlowSideAdapter;
import org.openkilda.floodlight.api.request.factory.EgressFlowSegmentRequestFactory;
import org.openkilda.floodlight.api.request.factory.FlowSegmentRequestProxiedFactory;
import org.openkilda.floodlight.api.request.factory.IngressFlowSegmentRequestFactory;
import org.openkilda.floodlight.api.request.factory.OneSwitchFlowRequestFactory;
import org.openkilda.floodlight.api.request.factory.TransitFlowSegmentRequestFactory;
import org.openkilda.floodlight.model.FlowSegmentMetadata;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.IslEndpoint;
import org.openkilda.model.MeterConfig;
import org.openkilda.model.PathSegment;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.EncapsulationResources;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.topology.flowhs.service.FlowCommandBuilder;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import lombok.NonNull;
import lombok.Value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class SpeakerFlowSegmentRequestBuilder implements FlowCommandBuilder {
    private final NoArgGenerator commandIdGenerator = Generators.timeBasedGenerator();
    private final FlowResourcesManager resourcesManager;

    public SpeakerFlowSegmentRequestBuilder(FlowResourcesManager resourcesManager) {
        this.resourcesManager = resourcesManager;
    }

    @Override
    public List<FlowSegmentRequestProxiedFactory> buildAll(
            CommandContext context, Flow flow, FlowPath forwardPath, FlowPath reversePath) {
        return makeAll(context, forwardPath, reversePath);
    }

    @Override
    public List<FlowSegmentRequestProxiedFactory> buildAllExceptIngress(CommandContext context, @NonNull Flow flow) {
        return buildAllExceptIngress(context, flow, flow.getForwardPath(), flow.getReversePath());
    }

    @Override
    public List<FlowSegmentRequestProxiedFactory> buildAllExceptIngress(
            CommandContext context, Flow flow, FlowPath forwardPath, FlowPath reversePath) {
        return makeAllExceptionIngress(context, forwardPath, reversePath);
    }

    @Override
    public List<FlowSegmentRequestProxiedFactory> buildIngressOnly(CommandContext context, @NonNull Flow flow) {
        return buildIngressOnly(context, flow, flow.getForwardPath(), flow.getReversePath());
    }

    @Override
    public List<FlowSegmentRequestProxiedFactory> buildIngressOnly(
            CommandContext context, Flow flow, FlowPath forwardPath, FlowPath reversePath) {
        return makeIngressOnly(context, forwardPath, reversePath);
    }

    private List<FlowSegmentRequestProxiedFactory> makeAll(
            CommandContext context, FlowPath pathAtoZ, FlowPath pathZtoA) {
        List<FlowSegmentRequestProxiedFactory> requests = new ArrayList<>();
        requests.addAll(makeRequests(
                pathAtoZ, pathZtoA, context, true, true, true));
        requests.addAll(makeRequests(
                pathZtoA, pathAtoZ, context, true, true, true));
        return requests;
    }

    private List<FlowSegmentRequestProxiedFactory> makeAllExceptionIngress(
            CommandContext context, FlowPath pathAtoZ, FlowPath pathZtoA) {
        List<FlowSegmentRequestProxiedFactory> requests = new ArrayList<>();
        requests.addAll(makeRequests(
                pathAtoZ, pathZtoA, context, false, true, true));
        requests.addAll(makeRequests(
                pathZtoA, pathAtoZ, context, false, true, true));
        return requests;
    }

    private List<FlowSegmentRequestProxiedFactory> makeIngressOnly(
            CommandContext context, FlowPath pathAtoZ, FlowPath pathZtoA) {
        List<FlowSegmentRequestProxiedFactory> requests = new ArrayList<>();
        requests.addAll(makeRequests(
                pathAtoZ, pathZtoA, context, true, false, false));
        requests.addAll(makeRequests(
                pathZtoA, pathAtoZ, context, true, false, false));
        return requests;
    }

    private List<FlowSegmentRequestProxiedFactory> makeRequests(
            @NonNull FlowPath path, @NonNull FlowPath oppositePath, CommandContext context,
            boolean doIngress, boolean doTransit, boolean doEgress) {
        final Flow flow = path.getFlow();
        final FlowSideAdapter ingressSide = FlowSideAdapter.makeIngressAdapter(flow, path);
        final FlowSideAdapter egressSide = FlowSideAdapter.makeEgressAdapter(flow, path);

        if (path.getSegments().isEmpty()) {
            // early exit for single-switch flows (also we must not call getEncapsulation for such flows)
            return doIngress
                    ? Collections.singletonList(makeOneSwitchRequest(context, path, ingressSide, egressSide))
                    : Collections.emptyList();
        }

        final FlowTransitEncapsulation encapsulation = getEncapsulation(
                flow.getEncapsulationType(), path, oppositePath);
        final List<FlowSegmentRequestProxiedFactory> requests = new ArrayList<>();

        PathSegment lastSegment = null;
        for (PathSegment segment : path.getSegments()) {
            if (lastSegment == null) {
                if (doIngress) {
                    requests.add(makeIngressRequest(context, path, encapsulation, ingressSide, segment, egressSide));
                }
            } else {
                if (doTransit) {
                    requests.add(makeTransitRequest(context, path, encapsulation, lastSegment, segment));
                }
            }
            lastSegment = segment;
        }

        if (lastSegment != null && doEgress) {
            requests.add(makeEgressRequest(context, path, encapsulation, lastSegment, egressSide, ingressSide));
        }

        return requests;
    }

    private FlowSegmentRequestProxiedFactory makeIngressRequest(
            CommandContext context, FlowPath path, FlowTransitEncapsulation encapsulation,
            FlowSideAdapter flowSide, PathSegment segment, FlowSideAdapter egressFlowSide) {
        PathSegmentSide segmentSide = makePathSegmentSourceSide(segment);

        UUID commandId = commandIdGenerator.generate();
        MessageContext messageContext = new MessageContext(commandId.toString(), context.getCorrelationId());
        return IngressFlowSegmentRequestFactory.builder()
                .messageContext(messageContext)
                .metadata(makeMetadata(path, ensureEqualMultiTableFlag(
                        flowSide.isMultiTableSegment(), segmentSide.isMultiTable(),
                        String.format("First flow(id:%s, path:%s) segment and flow level multi-table flag values are "
                                              + "incompatible to each other - flow(%s) != segment(%s)",
                                      path.getFlow().getFlowId(), path.getPathId(),
                                      flowSide.isMultiTableSegment(), segmentSide.isMultiTable()))))
                .endpoint(flowSide.getEndpoint())
                .meterConfig(getMeterConfig(path))
                .egressSwitchId(egressFlowSide.getEndpoint().getSwitchId())
                .islPort(segmentSide.getEndpoint().getPortNumber())
                .encapsulation(encapsulation)
                .build().makeProxyFactory();
    }

    private FlowSegmentRequestProxiedFactory makeTransitRequest(
            CommandContext context, FlowPath path, FlowTransitEncapsulation encapsulation,
            PathSegment ingress, PathSegment egress) {
        final PathSegmentSide inboundSide = makePathSegmentDestSide(ingress);
        final PathSegmentSide outboundSide = makePathSegmentSourceSide(egress);

        final IslEndpoint ingressEndpoint = inboundSide.getEndpoint();
        final IslEndpoint egressEndpoint = outboundSide.getEndpoint();

        assert ingressEndpoint.getSwitchId().equals(egressEndpoint.getSwitchId())
                : "Only neighbor segments can be used for for transit segment request creation";

        UUID commandId = commandIdGenerator.generate();
        MessageContext messageContext = new MessageContext(commandId.toString(), context.getCorrelationId());
        return TransitFlowSegmentRequestFactory.builder()
                .messageContext(messageContext)
                .switchId(ingressEndpoint.getSwitchId())
                .metadata(makeMetadata(path, ensureEqualMultiTableFlag(
                        inboundSide.isMultiTable(), outboundSide.isMultiTable(),
                        String.format(
                                "Flow(id:%s, path:%s) have incompatible multi-table flags between segments %s "
                                        + "and %s", path.getFlow().getFlowId(), path.getPathId(), ingress,
                                egress))))
                .ingressIslPort(ingressEndpoint.getPortNumber())
                .egressIslPort(egressEndpoint.getPortNumber())
                .encapsulation(encapsulation)
                .build().makeProxyFactory();
    }

    private FlowSegmentRequestProxiedFactory makeEgressRequest(
            CommandContext context, FlowPath path, FlowTransitEncapsulation encapsulation,
            PathSegment segment, FlowSideAdapter flowSide, FlowSideAdapter ingressFlowSide) {
        Flow flow = flowSide.getFlow();
        PathSegmentSide segmentSide = makePathSegmentDestSide(segment);

        UUID commandId = commandIdGenerator.generate();
        MessageContext messageContext = new MessageContext(commandId.toString(), context.getCorrelationId());

        return EgressFlowSegmentRequestFactory.builder()
                .messageContext(messageContext)
                .metadata(makeMetadata(path, ensureEqualMultiTableFlag(
                        segmentSide.isMultiTable(), flowSide.isMultiTableSegment(),
                        String.format("Last flow(id:%s, path:%s) segment and flow level multi-table flags value are "
                                              + "incompatible to each other - segment(%s) != flow(%s)",
                                      flow.getFlowId(), path.getPathId(), segmentSide.isMultiTable(),
                                      flowSide.isMultiTableSegment()))))
                .endpoint(flowSide.getEndpoint())
                .ingressEndpoint(ingressFlowSide.getEndpoint())
                .islPort(segmentSide.getEndpoint().getPortNumber())
                .encapsulation(encapsulation)
                .build().makeProxyFactory();

    }

    private FlowSegmentRequestProxiedFactory makeOneSwitchRequest(
            CommandContext context, FlowPath path, FlowSideAdapter ingressSide, FlowSideAdapter egressSide) {
        Flow flow = ingressSide.getFlow();

        UUID commandId = commandIdGenerator.generate();
        MessageContext messageContext = new MessageContext(commandId.toString(), context.getCorrelationId());
        return OneSwitchFlowRequestFactory.builder()
                .messageContext(messageContext)
                .metadata(makeMetadata(path, ensureEqualMultiTableFlag(
                        ingressSide.isMultiTableSegment(), egressSide.isMultiTableSegment(),
                        String.format("Flow(id:%s) have incompatible for one-switch flow per-side multi-table flags - "
                                              + "src(%s) != dst(%s)",
                                      flow.getFlowId(), flow.isSrcWithMultiTable(), flow.isDestWithMultiTable()))))
                .endpoint(ingressSide.getEndpoint())
                .meterConfig(getMeterConfig(path))
                .egressEndpoint(egressSide.getEndpoint())
                .build().makeProxyFactory();
    }

    private boolean ensureEqualMultiTableFlag(boolean ingress, boolean egress, String errorMessage) {
        if (ingress != egress) {
            throw new IllegalArgumentException(errorMessage);
        }
        return ingress;
    }

    private PathSegmentSide makePathSegmentSourceSide(PathSegment segment) {
        return new PathSegmentSide(
                new IslEndpoint(segment.getSrcSwitch().getSwitchId(), segment.getSrcPort()),
                segment.isSrcWithMultiTable());
    }

    private PathSegmentSide makePathSegmentDestSide(PathSegment segment) {
        return new PathSegmentSide(
                new IslEndpoint(segment.getDestSwitch().getSwitchId(), segment.getDestPort()),
                segment.isDestWithMultiTable());
    }

    private FlowSegmentMetadata makeMetadata(FlowPath path, boolean isMultitable) {
        Flow flow = path.getFlow();
        return new FlowSegmentMetadata(flow.getFlowId(), path.getCookie(), isMultitable);
    }

    @Value
    private static class PathSegmentSide {
        private final IslEndpoint endpoint;

        private boolean multiTable;
    }

    private MeterConfig getMeterConfig(FlowPath path) {
        if (path.getMeterId() == null) {
            return null;
        }
        return new MeterConfig(path.getMeterId(), path.getBandwidth());
    }

    private FlowTransitEncapsulation getEncapsulation(
            FlowEncapsulationType encapsulation, FlowPath path, FlowPath oppositePath) {
        EncapsulationResources resources = resourcesManager
                .getEncapsulationResources(path.getPathId(), oppositePath.getPathId(), encapsulation)
                .orElseThrow(() -> new IllegalStateException(format(
                        "No encapsulation resources found for flow path %s (opposite: %s)",
                        path.getPathId(), oppositePath.getPathId())));
        return new FlowTransitEncapsulation(resources.getTransitEncapsulationId(), resources.getEncapsulationType());
    }
}
