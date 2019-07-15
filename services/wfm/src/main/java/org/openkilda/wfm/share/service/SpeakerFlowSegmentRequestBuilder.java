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

import org.openkilda.floodlight.api.request.EgressFlowSegmentBlankRequest;
import org.openkilda.floodlight.api.request.FlowSegmentBlankGenericResolver;
import org.openkilda.floodlight.api.request.IngressFlowSegmentBlankRequest;
import org.openkilda.floodlight.api.request.OneSwitchFlowBlankRequest;
import org.openkilda.floodlight.api.request.TransitFlowSegmentBlankRequest;
import org.openkilda.floodlight.model.FlowSegmentMetadata;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.DetectConnectedDevices;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.MeterConfig;
import org.openkilda.model.PathSegment;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.EncapsulationResources;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.topology.flowhs.service.FlowCommandBuilder;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import lombok.NonNull;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class SpeakerFlowSegmentRequestBuilder implements FlowCommandBuilder {
    private final NoArgGenerator commandIdGenerator = Generators.timeBasedGenerator();
    private final FlowResourcesManager resourcesManager;
    private final Set<SwitchId> switchFilter = new HashSet<>();

    public SpeakerFlowSegmentRequestBuilder(FlowResourcesManager resourcesManager) {
        this(resourcesManager, new SwitchId[0]);
    }

    public SpeakerFlowSegmentRequestBuilder(FlowResourcesManager resourcesManager, SwitchId... switchOfInterest) {
        this.resourcesManager = resourcesManager;
        Collections.addAll(switchFilter, switchOfInterest);
    }

    @Override
    public List<FlowSegmentBlankGenericResolver> buildAll(
            CommandContext context, Flow flow, FlowPath forwardPath, FlowPath reversePath) {
        return makeAll(context, flow, forwardPath, reversePath);
    }

    @Override
    public List<FlowSegmentBlankGenericResolver> buildAllExceptIngress(CommandContext context, @NonNull Flow flow) {
        return buildAllExceptIngress(context, flow, flow.getForwardPath(), flow.getReversePath());
    }

    @Override
    public List<FlowSegmentBlankGenericResolver> buildAllExceptIngress(
            CommandContext context, Flow flow, FlowPath forwardPath, FlowPath reversePath) {
        return makeAllExceptionIngress(context, flow, forwardPath, reversePath);
    }

    @Override
    public List<FlowSegmentBlankGenericResolver> buildIngressOnly(CommandContext context, @NonNull Flow flow) {
        return buildIngressOnly(context, flow, flow.getForwardPath(), flow.getReversePath());
    }

    @Override
    public List<FlowSegmentBlankGenericResolver> buildIngressOnly(
            CommandContext context, Flow flow, FlowPath forwardPath, FlowPath reversePath) {
        return makeIngressOnly(context, flow, forwardPath, reversePath);
    }

    private List<FlowSegmentBlankGenericResolver> makeAll(
            CommandContext context, Flow flow, FlowPath pathAtoZ, FlowPath pathZtoA) {
        List<FlowSegmentBlankGenericResolver> requests = new ArrayList<>();
        requests.addAll(makeRequests(
                flow, pathAtoZ, pathZtoA, context, true, true, true));
        requests.addAll(makeRequests(
                flow, pathZtoA, pathAtoZ, context, true, true, true));
        return requests;
    }

    private List<FlowSegmentBlankGenericResolver> makeAllExceptionIngress(
            CommandContext context, Flow flow, FlowPath pathAtoZ, FlowPath pathZtoA) {
        List<FlowSegmentBlankGenericResolver> requests = new ArrayList<>();
        requests.addAll(makeRequests(
                flow, pathAtoZ, pathZtoA, context, false, true, true));
        requests.addAll(makeRequests(
                flow, pathZtoA, pathAtoZ, context, false, true, true));
        return requests;
    }

    private List<FlowSegmentBlankGenericResolver> makeIngressOnly(
            CommandContext context, Flow flow, FlowPath pathAtoZ, FlowPath pathZtoA) {
        List<FlowSegmentBlankGenericResolver> requests = new ArrayList<>();
        requests.addAll(makeRequests(
                flow, pathAtoZ, pathZtoA, context, true, false, false));
        requests.addAll(makeRequests(
                flow, pathZtoA, pathAtoZ, context, true, false, false));
        return requests;
    }

    private List<FlowSegmentBlankGenericResolver> makeRequests(
            @NonNull Flow flow, @NonNull FlowPath path, @NonNull FlowPath oppositePath, CommandContext context,
            boolean doEnter, boolean doTransit, boolean doExit) {
        FlowEndpoint ingressEndpoint = getIngressEndpoint(flow, path);
        FlowEndpoint egressEndpoint = getEgressEndpoint(flow, path);

        if (flow.isOneSwitchFlow()) {
            return makeOneSwitchRequest(path, context, ingressEndpoint, egressEndpoint);
        } else {
            return makeCommonRequest(
                    flow, path, oppositePath, context, ingressEndpoint, egressEndpoint, doEnter, doTransit, doExit);
        }
    }

    private List<FlowSegmentBlankGenericResolver> makeOneSwitchRequest(
            FlowPath path, CommandContext context, FlowEndpoint ingressEndpoint, FlowEndpoint egressEndpoint) {
        if (! isRequiredSwitch(ingressEndpoint.getDatapath())) {
            return Collections.emptyList();
        }
        return Collections.singletonList(makeOneSwitchFlowRequest(path, context, ingressEndpoint, egressEndpoint));
    }

    private List<FlowSegmentBlankGenericResolver> makeCommonRequest(
            Flow flow, FlowPath path, FlowPath oppositePath, CommandContext context,
            FlowEndpoint ingressEndpoint, FlowEndpoint egressEndpoint,
            boolean doEnter, boolean doTransit, boolean doExit) {
        ensureFlowPathValid(flow, path);

        List<FlowSegmentBlankGenericResolver> requests = new ArrayList<>();
        FlowTransitEncapsulation encapsulation = getEncapsulation(flow.getEncapsulationType(), path, oppositePath);

        if (doEnter && isRequiredSwitch(ingressEndpoint.getDatapath())) {
            requests.add(makeIngressSegmentRequest(path, context, ingressEndpoint, egressEndpoint, encapsulation));
        }

        if (doTransit) {
            requests.addAll(makeTransitRequests(path, context, encapsulation));
        }

        if (doExit && isRequiredSwitch(egressEndpoint.getDatapath())) {
            requests.add(makeEgressSegmentRequest(
                    path, context, egressEndpoint, ingressEndpoint, encapsulation));
        }

        return requests;
    }

    private List<FlowSegmentBlankGenericResolver> makeTransitRequests(
            FlowPath path, CommandContext context, FlowTransitEncapsulation encapsulation) {
        List<FlowSegmentBlankGenericResolver> requests = new ArrayList<>();
        List<PathSegment> segments = path.getSegments();
        for (int i = 1; i < segments.size(); i++) {
            PathSegment income = segments.get(i - 1);
            PathSegment outcome = segments.get(i);

            SwitchId datapath = income.getDestSwitch().getSwitchId();
            if (isRequiredSwitch(datapath)) {
                requests.add(makeTransitSegmentRequest(
                        path, context, datapath, income.getDestPort(),
                        outcome.getSrcPort(), encapsulation));
            }
        }

        return requests;
    }

    private FlowSegmentBlankGenericResolver makeOneSwitchFlowRequest(
            FlowPath path, CommandContext context, FlowEndpoint ingressEndpoint, FlowEndpoint egressEndpoint) {
        UUID commandId = commandIdGenerator.generate();
        MessageContext messageContext = new MessageContext(commandId.toString(), context.getCorrelationId());
        return OneSwitchFlowBlankRequest.buildResolver()
                .messageContext(messageContext)
                .commandId(commandId)
                .metadata(makeMetadata(path))
                .endpoint(ingressEndpoint)
                .meterConfig(getMeterConfig(path))
                .egressEndpoint(egressEndpoint)
                .build().makeGenericResolver();
    }

    private FlowSegmentBlankGenericResolver makeIngressSegmentRequest(
            FlowPath path, CommandContext context, FlowEndpoint endpoint, FlowEndpoint egressEndpoint,
            FlowTransitEncapsulation encapsulation) {
        UUID commandId = commandIdGenerator.generate();
        MessageContext messageContext = new MessageContext(commandId.toString(), context.getCorrelationId());

        PathSegment ingressSegment = path.getSegments().get(0);
        int islPort = ingressSegment.getSrcPort();

        return IngressFlowSegmentBlankRequest.buildResolver()
                .messageContext(messageContext)
                .commandId(commandId)
                .metadata(makeMetadata(path))
                .endpoint(endpoint)
                .meterConfig(getMeterConfig(path))
                .egressSwitchId(egressEndpoint.getDatapath())
                .islPort(islPort)
                .encapsulation(encapsulation)
                .build().makeGenericResolver();
    }

    private FlowSegmentBlankGenericResolver makeTransitSegmentRequest(
            FlowPath path, CommandContext context, SwitchId switchId, int ingressIslPort, int egressIslPort,
            FlowTransitEncapsulation encapsulation) {
        UUID commandId = commandIdGenerator.generate();
        MessageContext messageContext = new MessageContext(commandId.toString(), context.getCorrelationId());
        return TransitFlowSegmentBlankRequest.buildResolver()
                .messageContext(messageContext)
                .commandId(commandId)
                .switchId(switchId)
                .metadata(makeMetadata(path))
                .ingressIslPort(ingressIslPort)
                .egressIslPort(egressIslPort)
                .encapsulation(encapsulation)
                .build().makeGenericResolver();
    }

    private FlowSegmentBlankGenericResolver makeEgressSegmentRequest(
            FlowPath path, CommandContext context,
            FlowEndpoint egressEndpoint, FlowEndpoint ingressEndpoint, FlowTransitEncapsulation encapsulation) {

        List<PathSegment> segments = path.getSegments();
        PathSegment egressSegment = segments.get(segments.size() - 1);
        int islPort = egressSegment.getDestPort();

        UUID commandId = commandIdGenerator.generate();
        MessageContext messageContext = new MessageContext(commandId.toString(), context.getCorrelationId());

        return EgressFlowSegmentBlankRequest.buildResolver()
                .messageContext(messageContext)
                .commandId(commandId)
                .metadata(makeMetadata(path))
                .endpoint(egressEndpoint)
                .ingressEndpoint(ingressEndpoint)
                .islPort(islPort)
                .encapsulation(encapsulation)
                .build().makeGenericResolver();
    }

    private void ensureFlowPathValid(Flow flow, FlowPath path) {
        final List<PathSegment> segments = path.getSegments();
        if (CollectionUtils.isEmpty(segments)) {
            throw new IllegalArgumentException(String.format(
                    "Flow path with segments is required (flowId=%s, pathId=%s)", flow.getFlowId(), path.getPathId()));
        }

        if (!isIngressPathSegment(path, segments.get(0))
                || !isEgressPathSegment(path, segments.get(segments.size() - 1))) {
            throw new IllegalArgumentException(String.format(
                    "Flow's path segments do not start on flow endpoints (flowId=%s, pathId=%s)",
                    flow.getFlowId(), path.getPathId()));
        }
    }

    private boolean isIngressPathSegment(FlowPath path, PathSegment segment) {
        return path.getSrcSwitch().getSwitchId().equals(segment.getSrcSwitch().getSwitchId());
    }

    private boolean isEgressPathSegment(FlowPath path, PathSegment segment) {
        return path.getDestSwitch().getSwitchId().equals(segment.getDestSwitch().getSwitchId());
    }

    private boolean isRequiredSwitch(SwitchId swId) {
        if (switchFilter.isEmpty()) {
            return true;
        }
        return switchFilter.contains(swId);
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

    private FlowEndpoint getIngressEndpoint(Flow flow, FlowPath path) {
        if (path.getCookie().isMaskedAsForward()) {
            return getIngressEndpoint(flow);
        } else {
            return getEgressEndpoint(flow);
        }
    }

    private FlowEndpoint getIngressEndpoint(Flow flow) {
        DetectConnectedDevices trackConnectedDevices = flow.getDetectConnectedDevices();
        return new FlowEndpoint(
                flow.getSrcSwitch().getSwitchId(), flow.getSrcPort(), flow.getSrcVlan(),
                trackConnectedDevices.isSrcLldp() || trackConnectedDevices.isSrcArp());
    }

    private FlowEndpoint getEgressEndpoint(Flow flow, FlowPath path) {
        if (path.getCookie().isMaskedAsForward()) {
            return getEgressEndpoint(flow);
        } else {
            return getIngressEndpoint(flow);
        }
    }

    private FlowEndpoint getEgressEndpoint(Flow flow) {
        DetectConnectedDevices trackConnectedDevices = flow.getDetectConnectedDevices();
        return new FlowEndpoint(
                flow.getDestSwitch().getSwitchId(), flow.getDestPort(), flow.getDestVlan(),
                trackConnectedDevices.isDstLldp() || trackConnectedDevices.isDstArp());
    }

    private FlowSegmentMetadata makeMetadata(FlowPath path) {
        Flow flow = path.getFlow();
        return new FlowSegmentMetadata(flow.getFlowId(), path.getCookie(), false);
    }
}
