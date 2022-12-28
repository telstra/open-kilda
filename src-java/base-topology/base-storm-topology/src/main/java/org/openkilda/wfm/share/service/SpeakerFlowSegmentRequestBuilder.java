/* Copyright 2021 Telstra Open Source
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
import org.openkilda.floodlight.api.request.factory.EgressMirrorFlowSegmentRequestFactory;
import org.openkilda.floodlight.api.request.factory.FlowSegmentRequestFactory;
import org.openkilda.floodlight.api.request.factory.IngressFlowLoopSegmentRequestFactory;
import org.openkilda.floodlight.api.request.factory.IngressFlowSegmentRequestFactory;
import org.openkilda.floodlight.api.request.factory.IngressMirrorFlowSegmentRequestFactory;
import org.openkilda.floodlight.api.request.factory.OneSwitchFlowRequestFactory;
import org.openkilda.floodlight.api.request.factory.OneSwitchMirrorFlowRequestFactory;
import org.openkilda.floodlight.api.request.factory.TransitFlowLoopSegmentRequestFactory;
import org.openkilda.floodlight.api.request.factory.TransitFlowSegmentRequestFactory;
import org.openkilda.floodlight.model.FlowSegmentMetadata;
import org.openkilda.floodlight.model.RulesContext;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowMirror;
import org.openkilda.model.FlowMirrorPath;
import org.openkilda.model.FlowMirrorPoints;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathDirection;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.IslEndpoint;
import org.openkilda.model.MacAddress;
import org.openkilda.model.MeterConfig;
import org.openkilda.model.MirrorConfig;
import org.openkilda.model.MirrorConfig.MirrorConfigData;
import org.openkilda.model.MirrorConfig.PushVxlan;
import org.openkilda.model.NetworkEndpoint;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.EncapsulationResources;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.model.MirrorContext;
import org.openkilda.wfm.share.model.SpeakerRequestBuildContext;
import org.openkilda.wfm.share.model.SpeakerRequestBuildContext.PathContext;
import org.openkilda.wfm.topology.flowhs.service.FlowCommandBuilder;
import org.openkilda.wfm.topology.flowhs.service.FlowSegmentRequestFactoriesSequence;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import com.google.common.collect.Lists;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
public class SpeakerFlowSegmentRequestBuilder implements FlowCommandBuilder {
    // In the case of a path deletion operation, it is possible that there is no encapsulation resource for that path
    // in the database. In this case, we use this stub so as not to interrupt the delete operation. After that, excess
    // rules will remain on the switches, which the operator will have to synchronize.
    private static final FlowTransitEncapsulation DELETE_ENCAPSULATION_STUB =
            new FlowTransitEncapsulation(2, FlowEncapsulationType.TRANSIT_VLAN);

    private final NoArgGenerator commandIdGenerator = Generators.timeBasedGenerator();
    private final FlowResourcesManager resourcesManager;

    public SpeakerFlowSegmentRequestBuilder(FlowResourcesManager resourcesManager) {
        this.resourcesManager = resourcesManager;
    }

    @Override
    public List<FlowSegmentRequestFactory> buildAll(CommandContext context, Flow flow, FlowPath path,
                                                    SpeakerRequestBuildContext speakerRequestBuildContext) {
        return buildAll(context, flow, path, speakerRequestBuildContext, MirrorContext.DEFAULT);
    }

    @Override
    public List<FlowSegmentRequestFactory> buildAll(CommandContext context, Flow flow, FlowPath path,
                                                    SpeakerRequestBuildContext speakerRequestBuildContext,
                                                    MirrorContext mirrorContext) {
        return mergePair(makeRequests(
                context, flow, path, null, true, true, true, speakerRequestBuildContext, mirrorContext));
    }

    @Override
    public List<FlowSegmentRequestFactory> buildAll(CommandContext context, Flow flow, FlowPath forwardPath,
                                                    FlowPath reversePath,
                                                    SpeakerRequestBuildContext speakerRequestBuildContext) {
        return buildAll(context, flow, forwardPath, reversePath, speakerRequestBuildContext, MirrorContext.DEFAULT);
    }

    @Override
    public List<FlowSegmentRequestFactory> buildAll(CommandContext context, Flow flow, FlowPath forwardPath,
                                                    FlowPath reversePath,
                                                    SpeakerRequestBuildContext speakerRequestBuildContext,
                                                    MirrorContext mirrorContext) {
        return mergePair(makeRequests(
                context, flow, forwardPath, reversePath, true, true, true, speakerRequestBuildContext, mirrorContext));
    }

    @Override
    public List<FlowSegmentRequestFactory> buildAllExceptIngress(CommandContext context, @NonNull Flow flow) {
        return buildAllExceptIngress(context, flow, flow.getForwardPath(), flow.getReversePath());
    }

    @Override
    public List<FlowSegmentRequestFactory> buildAllExceptIngress(CommandContext context, Flow flow, FlowPath path) {
        return buildAllExceptIngress(context, flow, path, null, MirrorContext.DEFAULT);
    }

    @Override
    public List<FlowSegmentRequestFactory> buildAllExceptIngress(CommandContext context, Flow flow, FlowPath path,
                                                                 MirrorContext mirrorContext) {
        return buildAllExceptIngress(context, flow, path, null, mirrorContext);
    }

    @Override
    public List<FlowSegmentRequestFactory> buildAllExceptIngress(
            CommandContext context, Flow flow, FlowPath path, FlowPath oppositePath) {
        return buildAllExceptIngress(context, flow, path, oppositePath, MirrorContext.DEFAULT);
    }

    @Override
    public List<FlowSegmentRequestFactory> buildAllExceptIngress(
            CommandContext context, Flow flow, FlowPath path, FlowPath oppositePath, MirrorContext mirrorContext) {
        return mergePair(makeRequests(context, flow, path, oppositePath, false, true, true,
                SpeakerRequestBuildContext.getEmpty(), mirrorContext));
    }

    @Override
    public Pair<FlowSegmentRequestFactoriesSequence, FlowSegmentRequestFactoriesSequence>
            buildAllExceptIngressSeverally(CommandContext context, Flow flow, FlowPath path, FlowPath oppositePath) {
        return makeRequests(context, flow, path, oppositePath, false, true, true,
                SpeakerRequestBuildContext.getEmpty(), MirrorContext.DEFAULT);
    }

    @Override
    public List<FlowSegmentRequestFactory> buildIngressOnly(
            CommandContext context, @NonNull Flow flow, SpeakerRequestBuildContext speakerRequestBuildContext) {
        return buildIngressOnly(context, flow, flow.getForwardPath(), flow.getReversePath(),
                speakerRequestBuildContext);
    }

    @Override
    public List<FlowSegmentRequestFactory> buildIngressOnly(
            CommandContext context, Flow flow, FlowPath path, FlowPath oppositePath,
            SpeakerRequestBuildContext speakerRequestBuildContext) {
        return buildIngressOnly(context, flow, path, oppositePath, speakerRequestBuildContext, MirrorContext.DEFAULT);
    }

    @Override
    public List<FlowSegmentRequestFactory> buildIngressOnly(
            CommandContext context, Flow flow, FlowPath path, FlowPath oppositePath,
            SpeakerRequestBuildContext speakerRequestBuildContext, MirrorContext mirrorContext) {
        return mergePair(makeRequests(context, flow, path, oppositePath, true, false, false,
                speakerRequestBuildContext, mirrorContext));
    }

    @Override
    public Pair<FlowSegmentRequestFactoriesSequence, FlowSegmentRequestFactoriesSequence> buildIngressOnlySeverally(
            CommandContext context, Flow flow, FlowPath path, FlowPath oppositePath,
            SpeakerRequestBuildContext speakerRequestBuildContext) {
        return makeRequests(context, flow, path, oppositePath, true, false, false,
                speakerRequestBuildContext, MirrorContext.DEFAULT);
    }

    @Override
    public List<FlowSegmentRequestFactory> buildIngressOnlyOneDirection(
            CommandContext context, Flow flow, FlowPath path, FlowPath oppositePath,
            PathContext pathContext) {
        return makeRequests(context, flow, path, oppositePath, true, false, false,
                pathContext, MirrorContext.DEFAULT);
    }

    @Override
    public List<FlowSegmentRequestFactory> buildIngressOnlyOneDirection(
            CommandContext context, Flow flow, FlowPath path, FlowPath oppositePath,
            PathContext pathContext, MirrorContext mirrorContext) {
        return makeRequests(context, flow, path, oppositePath, true, false, false,
                pathContext, mirrorContext);
    }

    @Override
    public List<FlowSegmentRequestFactory> buildEgressOnly(
            CommandContext context, Flow flow, FlowPath path, FlowPath oppositePath) {
        return buildEgressOnly(context, flow, path, oppositePath, MirrorContext.DEFAULT);
    }

    @Override
    public List<FlowSegmentRequestFactory> buildEgressOnly(
            CommandContext context, Flow flow, FlowPath path, FlowPath oppositePath, MirrorContext mirrorContext) {
        return mergePair(makeRequests(
                context, flow, path, oppositePath, false, false, true, SpeakerRequestBuildContext.getEmpty(),
                mirrorContext));
    }

    @Override
    public List<FlowSegmentRequestFactory> buildEgressOnlyOneDirection(
            CommandContext context, Flow flow, FlowPath path, FlowPath oppositePath) {

        return buildEgressOnlyOneDirection(context, flow, path, oppositePath, MirrorContext.DEFAULT);

    }

    @Override
    public List<FlowSegmentRequestFactory> buildEgressOnlyOneDirection(
            CommandContext context, Flow flow, FlowPath path, FlowPath oppositePath, MirrorContext mirrorContext) {

        return makeRequests(context, flow, path, oppositePath, false, false, true,
                SpeakerRequestBuildContext.getEmpty().getForward(), mirrorContext);

    }

    // NOTE(tdurakov): partially duplicating method below due to unreliable swap in it
    private List<FlowSegmentRequestFactory> makeRequests(
            CommandContext context, Flow flow, FlowPath path, FlowPath oppositePath, boolean doIngress,
            boolean doTransit, boolean doEgress, PathContext pathContext, MirrorContext mirrorContext) {
        if (path == null || oppositePath == null) {
            throw new IllegalArgumentException("Both flow paths must be not null");
        }
        FlowTransitEncapsulation encapsulation = null;
        if (!flow.isOneSwitchFlow()) {
            encapsulation = getEncapsulation(
                    flow.getEncapsulationType(), path.getPathId(), oppositePath.getPathId());
        }

        return new ArrayList<>(makePathRequests(flow, path, context, encapsulation,
                doIngress, doTransit, doEgress, createRulesContext(pathContext), mirrorContext));
    }

    private Pair<FlowSegmentRequestFactoriesSequence, FlowSegmentRequestFactoriesSequence> makeRequests(
            CommandContext context, Flow flow, FlowPath path, FlowPath oppositePath,
            boolean doIngress, boolean doTransit, boolean doEgress,
            SpeakerRequestBuildContext speakerRequestBuildContext, MirrorContext mirrorContext) {
        boolean isSwapped = false;

        // TODO: this swap is weird, need to clean this up and not to rely on luck.
        if (path == null) {
            isSwapped = true;
            path = oppositePath;
            oppositePath = null;
            speakerRequestBuildContext = SpeakerRequestBuildContext.builder()
                    .forward(speakerRequestBuildContext.getReverse())
                    .reverse(speakerRequestBuildContext.getForward())
                    .build();
        }
        if (path == null) {
            throw new IllegalArgumentException("At least one flow path must be not null");
        }

        boolean isDeleteOperation = speakerRequestBuildContext.isDeleteOperation();
        FlowTransitEncapsulation encapsulation = null;
        if (!flow.isOneSwitchFlow()) {
            try {
                encapsulation = getEncapsulation(
                        flow.getEncapsulationType(), path.getPathId(),
                        oppositePath != null ? oppositePath.getPathId() : null);
            } catch (IllegalStateException e) {
                if (!isDeleteOperation) {
                    throw e;
                }
                encapsulation = DELETE_ENCAPSULATION_STUB;
            }
        }

        FlowSegmentRequestFactoriesSequence left = new FlowSegmentRequestFactoriesSequence(
                makePathRequests(flow, path, context, encapsulation,
                doIngress, doTransit, doEgress, createRulesContext(speakerRequestBuildContext.getForward()),
                mirrorContext));
        FlowSegmentRequestFactoriesSequence right = new FlowSegmentRequestFactoriesSequence();
        if (oppositePath != null) {
            if (!flow.isOneSwitchFlow()) {
                try {
                    encapsulation = getEncapsulation(
                            flow.getEncapsulationType(), oppositePath.getPathId(), path.getPathId());
                } catch (IllegalStateException e) {
                    if (!isDeleteOperation) {
                        throw e;
                    }
                    encapsulation = DELETE_ENCAPSULATION_STUB;
                }
            }
            right.addAll(makePathRequests(flow, oppositePath, context, encapsulation, doIngress, doTransit, doEgress,
                    createRulesContext(speakerRequestBuildContext.getReverse()), mirrorContext));
        }

        if (isSwapped) {
            return Pair.of(right, left);
        }
        return Pair.of(left, right);
    }

    private RulesContext createRulesContext(PathContext pathContext) {
        return new RulesContext(
                pathContext.isRemoveCustomerPortRule(),
                pathContext.isRemoveCustomerPortLldpRule(),
                pathContext.isRemoveCustomerPortArpRule(),
                pathContext.isRemoveOuterVlanMatchSharedRule(),
                pathContext.isUpdateMeter(),
                pathContext.isRemoveServer42InputRule(),
                pathContext.isRemoveServer42IngressRule(),
                pathContext.isRemoveServer42OuterVlanMatchSharedRule(),
                pathContext.isInstallServer42InputRule(),
                pathContext.isInstallServer42IngressRule(),
                pathContext.isInstallServer42OuterVlanMatchSharedRule(),
                pathContext.getServer42Port(),
                pathContext.getServer42MacAddress());
    }

    @SuppressWarnings("squid:S00107")
    private List<FlowSegmentRequestFactory> makePathRequests(
            @NonNull Flow flow, @NonNull FlowPath path, CommandContext context, FlowTransitEncapsulation encapsulation,
            boolean doIngress, boolean doTransit, boolean doEgress, RulesContext rulesContext,
            MirrorContext mirrorContext) {
        final FlowSideAdapter ingressSide = FlowSideAdapter.makeIngressAdapter(flow, path);
        final FlowSideAdapter egressSide = FlowSideAdapter.makeEgressAdapter(flow, path);

        final List<FlowSegmentRequestFactory> requests = new ArrayList<>();

        PathSegment lastSegment = null;
        for (PathSegment segment : path.getSegments()) {
            if (lastSegment == null) {
                if (doIngress) {
                    requests.addAll(makeIngressSegmentRequests(context, path, encapsulation, ingressSide, segment,
                            egressSide, rulesContext, mirrorContext, flow.getVlanStatistics()));
                    if (ingressLoopRuleRequired(flow, ingressSide)) {
                        requests.addAll(makeLoopRequests(
                                context, path, encapsulation, ingressSide, egressSide, segment));
                    }
                }
            } else {
                if (doTransit) {
                    requests.add(makeTransitSegmentRequest(context, path, encapsulation, lastSegment, segment));
                }
            }
            lastSegment = segment;
        }

        if (lastSegment != null) {
            if (doEgress) {
                requests.addAll(makeEgressSegmentRequests(context, path, encapsulation, lastSegment, egressSide,
                        ingressSide, mirrorContext));
            }
        } else if (flow.isOneSwitchFlow()) {
            // one switch flow (path without path segments)
            requests.addAll(makeOneSwitchRequest(context, path, ingressSide, egressSide, rulesContext, mirrorContext,
                    flow.getVlanStatistics()));
            if (singleSwitchLoopRuleRequired(flow)) {
                requests.add(makeSingleSwitchIngressLoopRequest(context, path, ingressSide));
            }
        }

        return requests;
    }

    private List<FlowSegmentRequestFactory> makeIngressSegmentRequests(
            CommandContext context, FlowPath path, FlowTransitEncapsulation encapsulation,
            FlowSideAdapter flowSide, PathSegment segment, FlowSideAdapter egressFlowSide,
            RulesContext rulesContext, MirrorContext mirrorContext, Set<Integer> statVlans) {
        PathSegmentSide segmentSide = makePathSegmentSourceSide(segment);

        UUID commandId = commandIdGenerator.generate();
        MessageContext messageContext = new MessageContext(commandId.toString(), context.getCorrelationId());

        FlowSegmentMetadata metadata = makeMetadata(path, ensureEqualMultiTableFlag(
                path.isSrcWithMultiTable(), segmentSide.isMultiTable(),
                format("First flow(id:%s, path:%s) segment and flow path level multi-table flag values "
                                + "are incompatible to each other - flow path(%s) != segment(%s)",
                        path.getFlow().getFlowId(), path.getPathId(),
                        path.isSrcWithMultiTable(), segmentSide.isMultiTable())));

        List<FlowSegmentRequestFactory> ingressFactories = new ArrayList<>();

        if (!mirrorContext.isBuildMirrorFactoryOnly()) {
            ingressFactories.add(IngressFlowSegmentRequestFactory.builder()
                    .messageContext(messageContext)
                    .metadata(metadata)
                    .endpoint(flowSide.getEndpoint())
                    .meterConfig(getMeterConfig(path))
                    .egressSwitchId(egressFlowSide.getEndpoint().getSwitchId())
                    .islPort(segmentSide.getEndpoint().getPortNumber())
                    .encapsulation(encapsulation)
                    .rulesContext(rulesContext)
                    .statVlans(statVlans)
                    .build());
        }

        Optional<MirrorConfig> mirrorConfig = makeMirrorConfig(path, segmentSide.getEndpoint(), mirrorContext,
                encapsulation.getType());
        if (mirrorConfig.isPresent() || mirrorContext.isRemoveFlowOperation()) {
            FlowSegmentCookie mirrorCookie = path.getCookie().toBuilder().mirror(true).build();
            ingressFactories.add(IngressMirrorFlowSegmentRequestFactory.builder()
                    .messageContext(new MessageContext(
                            commandIdGenerator.generate().toString(), context.getCorrelationId()))
                    .metadata(makeMetadata(metadata.getFlowId(), mirrorCookie, metadata.isMultiTable()))
                    .endpoint(flowSide.getEndpoint())
                    .meterConfig(getMeterConfig(path))
                    .egressSwitchId(egressFlowSide.getEndpoint().getSwitchId())
                    .islPort(segmentSide.getEndpoint().getPortNumber())
                    .encapsulation(encapsulation)
                    .rulesContext(rulesContext.toBuilder().updateMeter(false)
                            .installServer42IngressRule(false)
                            .installServer42InputRule(false)
                            .installServer42OuterVlanMatchSharedRule(false)
                            .build())
                    .mirrorConfig(mirrorConfig.orElse(null))
                    .statVlans(new HashSet<>())
                    .build());
        }

        return ingressFactories;
    }

    private boolean ingressLoopRuleRequired(Flow flow, FlowSideAdapter flowSideAdapter) {
        return flow.isLooped() && flowSideAdapter.getEndpoint().getSwitchId().equals(flow.getLoopSwitchId());
    }

    private List<FlowSegmentRequestFactory> makeLoopRequests(
            CommandContext context, FlowPath path, FlowTransitEncapsulation encapsulation,
            FlowSideAdapter ingressSide, FlowSideAdapter egressSide, PathSegment segment) {
        List<FlowSegmentRequestFactory> result = new ArrayList<>(2);
        PathSegmentSide segmentSide = makePathSegmentSourceSide(segment);

        UUID commandId = commandIdGenerator.generate();
        MessageContext messageContext = new MessageContext(commandId.toString(), context.getCorrelationId());
        FlowSegmentCookie cookie = path.getCookie().toBuilder().looped(true).build();

        result.add(IngressFlowLoopSegmentRequestFactory.builder()
                .messageContext(messageContext)
                .metadata(makeMetadata(path.getFlow().getFlowId(), cookie, segmentSide.isMultiTable()))
                .endpoint(ingressSide.getEndpoint())
                .build());

        FlowPathDirection reverse = cookie.getDirection() == FlowPathDirection.FORWARD ? FlowPathDirection.REVERSE
                : FlowPathDirection.FORWARD;
        Cookie transitCookie = path.getCookie().toBuilder().looped(true).direction(reverse).build();
        result.add(TransitFlowLoopSegmentRequestFactory.builder()
                .messageContext(messageContext)
                .switchId(segment.getSrcSwitch().getSwitchId())
                .egressSwitchId(egressSide.getEndpoint().getSwitchId())
                .metadata(makeMetadata(path.getFlow().getFlowId(), transitCookie, segmentSide.isMultiTable()))
                .port(segment.getSrcPort())
                .encapsulation(encapsulation)
                .build());
        return result;
    }

    private boolean singleSwitchLoopRuleRequired(Flow flow) {
        return flow.isLooped() && flow.isOneSwitchFlow();
    }

    private FlowSegmentRequestFactory makeSingleSwitchIngressLoopRequest(
            CommandContext context, FlowPath path, FlowSideAdapter flowSide) {
        UUID commandId = commandIdGenerator.generate();
        MessageContext messageContext = new MessageContext(commandId.toString(), context.getCorrelationId());
        Cookie cookie = path.getCookie().toBuilder().looped(true).build();
        return IngressFlowLoopSegmentRequestFactory.builder()
                .messageContext(messageContext)
                .metadata(makeMetadata(path.getFlow().getFlowId(), cookie, path.isSrcWithMultiTable()))
                .endpoint(flowSide.getEndpoint())
                .build();
    }

    private FlowSegmentRequestFactory makeTransitSegmentRequest(
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
                        format(
                                "Flow(id:%s, path:%s) have incompatible multi-table flags between segments %s "
                                        + "and %s", path.getFlow().getFlowId(), path.getPathId(), ingress,
                                egress))))
                .ingressIslPort(ingressEndpoint.getPortNumber())
                .egressIslPort(egressEndpoint.getPortNumber())
                .encapsulation(encapsulation)
                .build();
    }

    private List<FlowSegmentRequestFactory> makeEgressSegmentRequests(
            CommandContext context, FlowPath path, FlowTransitEncapsulation encapsulation,
            PathSegment segment, FlowSideAdapter flowSide, FlowSideAdapter ingressFlowSide,
            MirrorContext mirrorContext) {
        Flow flow = flowSide.getFlow();
        PathSegmentSide segmentSide = makePathSegmentDestSide(segment);

        UUID commandId = commandIdGenerator.generate();
        MessageContext messageContext = new MessageContext(commandId.toString(), context.getCorrelationId());

        FlowSegmentMetadata metadata = makeMetadata(path, ensureEqualMultiTableFlag(
                segmentSide.isMultiTable(), path.isDestWithMultiTable(),
                format("Last flow(id:%s, path:%s) segment and flow path level multi-table flags value "
                                + "are incompatible to each other - segment(%s) != flow path(%s)",
                        flow.getFlowId(), path.getPathId(), segmentSide.isMultiTable(),
                        path.isDestWithMultiTable())));

        List<FlowSegmentRequestFactory> egressFactories = new ArrayList<>();
        if (!mirrorContext.isBuildMirrorFactoryOnly()) {
            egressFactories.add(EgressFlowSegmentRequestFactory.builder()
                    .messageContext(messageContext)
                    .metadata(metadata)
                    .endpoint(flowSide.getEndpoint())
                    .ingressEndpoint(ingressFlowSide.getEndpoint())
                    .islPort(segmentSide.getEndpoint().getPortNumber())
                    .encapsulation(encapsulation)
                    .build());
        }

        Optional<MirrorConfig> mirrorConfig = makeMirrorConfig(path, flowSide.getEndpoint(), mirrorContext,
                encapsulation.getType());
        if (mirrorConfig.isPresent() || mirrorContext.isRemoveFlowOperation()) {
            FlowSegmentCookie mirrorCookie = path.getCookie().toBuilder().mirror(true).build();
            egressFactories.add(EgressMirrorFlowSegmentRequestFactory.builder()
                    .messageContext(new MessageContext(
                            commandIdGenerator.generate().toString(), context.getCorrelationId()))
                    .metadata(makeMetadata(metadata.getFlowId(), mirrorCookie, metadata.isMultiTable()))
                    .endpoint(flowSide.getEndpoint())
                    .ingressEndpoint(ingressFlowSide.getEndpoint())
                    .islPort(segmentSide.getEndpoint().getPortNumber())
                    .encapsulation(encapsulation)
                    .mirrorConfig(mirrorConfig.orElse(null))
                    .build());
        }

        return egressFactories;
    }

    private List<FlowSegmentRequestFactory> makeOneSwitchRequest(
            CommandContext context, FlowPath path, FlowSideAdapter ingressSide, FlowSideAdapter egressSide,
            RulesContext rulesContext, MirrorContext mirrorContext, Set<Integer> statVlans) {
        Flow flow = ingressSide.getFlow();

        UUID commandId = commandIdGenerator.generate();
        MessageContext messageContext = new MessageContext(commandId.toString(), context.getCorrelationId());

        FlowSegmentMetadata metadata = makeMetadata(path, ensureEqualMultiTableFlag(
                path.isSrcWithMultiTable(), path.isDestWithMultiTable(),
                format("Flow(id:%s) have incompatible for one-switch flow per-side multi-table flags - "
                                + "src(%s) != dst(%s)",
                        flow.getFlowId(), path.isSrcWithMultiTable(), path.isDestWithMultiTable())));

        List<FlowSegmentRequestFactory> oneSwitchFactories = new ArrayList<>();
        if (!mirrorContext.isBuildMirrorFactoryOnly()) {
            oneSwitchFactories.add(OneSwitchFlowRequestFactory.builder()
                    .messageContext(messageContext)
                    .metadata(metadata)
                    .endpoint(ingressSide.getEndpoint())
                    .meterConfig(getMeterConfig(path))
                    .egressEndpoint(egressSide.getEndpoint())
                    .rulesContext(rulesContext)
                    .statVlans(statVlans)
                    .build());
        }

        Optional<MirrorConfig> mirrorConfig = makeMirrorConfig(path, egressSide.getEndpoint(), mirrorContext,
                flow.getEncapsulationType());
        if (mirrorConfig.isPresent() || mirrorContext.isRemoveFlowOperation()) {
            FlowSegmentCookie mirrorCookie = path.getCookie().toBuilder().mirror(true).build();
            oneSwitchFactories.add(OneSwitchMirrorFlowRequestFactory.builder()
                    .messageContext(new MessageContext(
                            commandIdGenerator.generate().toString(), context.getCorrelationId()))
                    .metadata(makeMetadata(metadata.getFlowId(), mirrorCookie, metadata.isMultiTable()))
                    .endpoint(ingressSide.getEndpoint())
                    .meterConfig(getMeterConfig(path))
                    .egressEndpoint(egressSide.getEndpoint())
                    .rulesContext(rulesContext)
                    .mirrorConfig(mirrorConfig.orElse(null))
                    .statVlans(new HashSet<>())
                    .build());
        }

        return oneSwitchFactories;
    }

    private boolean ensureEqualMultiTableFlag(boolean ingress, boolean egress, String errorMessage) {
        if (ingress != egress) {
            throw new IllegalArgumentException(errorMessage);
        }
        return ingress;
    }

    private PathSegmentSide makePathSegmentSourceSide(PathSegment segment) {
        return new PathSegmentSide(
                new IslEndpoint(segment.getSrcSwitchId(), segment.getSrcPort()),
                segment.isSrcWithMultiTable());
    }

    private PathSegmentSide makePathSegmentDestSide(PathSegment segment) {
        return new PathSegmentSide(
                new IslEndpoint(segment.getDestSwitchId(), segment.getDestPort()),
                segment.isDestWithMultiTable());
    }

    private FlowSegmentMetadata makeMetadata(String flowId, Cookie cookie, boolean isMultitable) {
        return new FlowSegmentMetadata(flowId, cookie, isMultitable);
    }

    private FlowSegmentMetadata makeMetadata(FlowPath path, boolean isMultitable) {
        return makeMetadata(path.getFlow().getFlowId(), path.getCookie(), isMultitable);
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
            FlowEncapsulationType encapsulation, PathId pathId, PathId oppositePathId) {
        EncapsulationResources resources = resourcesManager
                .getEncapsulationResources(pathId, oppositePathId, encapsulation)
                .orElseThrow(() -> new IllegalStateException(format(
                        "No %s encapsulation resources found for flow path %s (opposite: %s).",
                        encapsulation, pathId, oppositePathId)));
        return new FlowTransitEncapsulation(resources.getTransitEncapsulationId(), resources.getEncapsulationType());
    }

    private Optional<MirrorConfig> makeMirrorConfig(
            @NonNull FlowPath flowPath, @NonNull NetworkEndpoint endpoint, MirrorContext mirrorContext,
            FlowEncapsulationType flowEncapsulationType) {
        if (!mirrorContext.isRemoveGroup()) {
            return Optional.empty();
        }

        FlowMirrorPoints flowMirrorPoints = flowPath.getFlowMirrorPointsSet().stream()
                .filter(mirrorPoints -> endpoint.getSwitchId().equals(mirrorPoints.getMirrorSwitchId()))
                .findFirst().orElse(null);

        if (flowMirrorPoints != null) {
            Set<MirrorConfigData> mirrorConfigDataSet = getMirrorConfigData(flowMirrorPoints, flowEncapsulationType);

            if (!mirrorConfigDataSet.isEmpty()) {
                return Optional.of(MirrorConfig.builder()
                        .groupId(flowMirrorPoints.getMirrorGroupId())
                        .flowPort(endpoint.getPortNumber())
                        .mirrorConfigDataSet(mirrorConfigDataSet)
                        .addNewGroup(mirrorContext.isAddNewGroup())
                        .build());
            }

            return Optional.of(MirrorConfig.builder()
                    .groupId(flowMirrorPoints.getMirrorGroupId())
                    .flowPort(endpoint.getPortNumber())
                    .build());
        }

        return Optional.empty();
    }

    private Set<MirrorConfigData> getMirrorConfigData(
            FlowMirrorPoints flowMirrorPoints, FlowEncapsulationType flowEncapsulationType) {
        Map<PathId, EncapsulationResources> encapsulationMap = resourcesManager.getMirrorEncapsulationMap(
                Lists.newArrayList(flowMirrorPoints), flowEncapsulationType);
        Set<MirrorConfigData> result = new HashSet<>();
        for (FlowMirror flowMirror : flowMirrorPoints.getFlowMirrors()) {
            for (FlowMirrorPath mirrorPath : flowMirror.getMirrorPaths()) {
                if (mirrorPath.isForward() && !mirrorPath.isDummy()) {
                    if (mirrorPath.isSingleSwitchPath()) {
                        result.add(new MirrorConfigData(
                                flowMirror.getEgressPort(), flowMirror.getEgressOuterVlan(), null));    
                    } else {
                        buildMultiSwitchMirrorConfig(mirrorPath, flowEncapsulationType, encapsulationMap)
                                .ifPresent(result::add);
                    }
                }
            }
        }
        return result;
    }
    
    private static Optional<MirrorConfigData> buildMultiSwitchMirrorConfig(
            FlowMirrorPath mirrorPath, FlowEncapsulationType flowEncapsulationType,
            Map<PathId, EncapsulationResources> mirrorEncapsulationMap) {
        if (mirrorPath.getSegments() == null || mirrorPath.getSegments().isEmpty()) {
            log.error(format("Multi-switch flow mirror path %s has no segments",
                    mirrorPath.getMirrorPathId()));
            return Optional.empty();
        }
        
        int outPort = mirrorPath.getSegments().get(0).getSrcPort();
        int encapsulationId = mirrorEncapsulationMap.get(mirrorPath.getMirrorPathId()).getTransitEncapsulationId();
        switch (flowEncapsulationType) {
            case TRANSIT_VLAN:
                return Optional.of(new MirrorConfigData(outPort, encapsulationId, null));
            case VXLAN:
                PushVxlan vxlan = new PushVxlan(encapsulationId, new MacAddress(
                        mirrorPath.getEgressSwitchId().toMacAddress()));
                return Optional.of(new MirrorConfigData(outPort, null, vxlan));
            default:
                log.error(format("Unknown encapsulation type %s for flow mirror path %s",
                        flowEncapsulationType, mirrorPath.getMirrorPathId()));
                return Optional.empty();
        }
    }

    private static List<FlowSegmentRequestFactory> mergePair(
            Pair<FlowSegmentRequestFactoriesSequence, FlowSegmentRequestFactoriesSequence> source) {
        List<FlowSegmentRequestFactory> results = new ArrayList<>(source.getLeft());
        results.addAll(source.getRight());
        return results;
    }
}
