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

package org.openkilda.northbound.service.impl;

import static java.lang.String.format;
import static org.openkilda.messaging.Utils.FLOW_ID;
import static org.openkilda.northbound.utils.async.AsyncUtils.collectResponses;

import org.openkilda.config.provider.ConfigurationProvider;
import org.openkilda.messaging.Destination;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.flow.FlowCreateRequest;
import org.openkilda.messaging.command.flow.FlowDeleteRequest;
import org.openkilda.messaging.command.flow.FlowPathSwapRequest;
import org.openkilda.messaging.command.flow.FlowPingRequest;
import org.openkilda.messaging.command.flow.FlowReadRequest;
import org.openkilda.messaging.command.flow.FlowRerouteRequest;
import org.openkilda.messaging.command.flow.FlowUpdateRequest;
import org.openkilda.messaging.command.flow.FlowsDumpRequest;
import org.openkilda.messaging.command.flow.MeterModifyRequest;
import org.openkilda.messaging.command.flow.SwapFlowEndpointRequest;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.error.MessageException;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.flow.FlowHistoryData;
import org.openkilda.messaging.info.flow.FlowInfoData;
import org.openkilda.messaging.info.flow.FlowOperation;
import org.openkilda.messaging.info.flow.FlowPingResponse;
import org.openkilda.messaging.info.flow.FlowReadResponse;
import org.openkilda.messaging.info.flow.FlowRerouteResponse;
import org.openkilda.messaging.info.flow.FlowResponse;
import org.openkilda.messaging.info.flow.FlowStatusResponse;
import org.openkilda.messaging.info.flow.SwapFlowResponse;
import org.openkilda.messaging.info.meter.FlowMeterEntries;
import org.openkilda.messaging.info.rule.FlowApplyActions;
import org.openkilda.messaging.info.rule.FlowEntry;
import org.openkilda.messaging.info.rule.FlowSetFieldAction;
import org.openkilda.messaging.info.rule.SwitchFlowEntries;
import org.openkilda.messaging.model.BidirectionalFlowDto;
import org.openkilda.messaging.model.FlowDto;
import org.openkilda.messaging.model.FlowPathDto;
import org.openkilda.messaging.model.FlowPathDto.FlowProtectedPathDto;
import org.openkilda.messaging.nbtopology.request.FlowConnectedDeviceRequest;
import org.openkilda.messaging.nbtopology.request.FlowPatchRequest;
import org.openkilda.messaging.nbtopology.request.GetFlowHistoryRequest;
import org.openkilda.messaging.nbtopology.request.GetFlowPathRequest;
import org.openkilda.messaging.nbtopology.response.GetFlowPathResponse;
import org.openkilda.messaging.payload.flow.DiverseGroupPayload;
import org.openkilda.messaging.payload.flow.FlowCreatePayload;
import org.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.openkilda.messaging.payload.flow.FlowPathPayload;
import org.openkilda.messaging.payload.flow.FlowPathPayload.FlowProtectedPath;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.messaging.payload.flow.FlowReroutePayload;
import org.openkilda.messaging.payload.flow.FlowResponsePayload;
import org.openkilda.messaging.payload.flow.FlowState;
import org.openkilda.messaging.payload.flow.FlowUpdatePayload;
import org.openkilda.messaging.payload.flow.GroupFlowPathPayload;
import org.openkilda.messaging.payload.history.FlowEventPayload;
import org.openkilda.model.Cookie;
import org.openkilda.model.EncapsulationId;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.SwitchId;
import org.openkilda.northbound.converter.ConnectedDeviceMapper;
import org.openkilda.northbound.converter.FlowMapper;
import org.openkilda.northbound.converter.PathMapper;
import org.openkilda.northbound.dto.BatchResults;
import org.openkilda.northbound.dto.v1.flows.FlowConnectedDevicesResponse;
import org.openkilda.northbound.dto.v1.flows.FlowPatchDto;
import org.openkilda.northbound.dto.v1.flows.FlowValidationDto;
import org.openkilda.northbound.dto.v1.flows.PathDiscrepancyDto;
import org.openkilda.northbound.dto.v1.flows.PingInput;
import org.openkilda.northbound.dto.v1.flows.PingOutput;
import org.openkilda.northbound.dto.v2.flows.FlowRequestV2;
import org.openkilda.northbound.dto.v2.flows.FlowRerouteResponseV2;
import org.openkilda.northbound.dto.v2.flows.FlowResponseV2;
import org.openkilda.northbound.dto.v2.flows.SwapFlowEndpointPayload;
import org.openkilda.northbound.messaging.MessagingChannel;
import org.openkilda.northbound.service.FlowService;
import org.openkilda.northbound.service.SwitchService;
import org.openkilda.northbound.utils.CorrelationIdFactory;
import org.openkilda.northbound.utils.RequestCorrelationId;
import org.openkilda.persistence.Neo4jConfig;
import org.openkilda.persistence.NetworkConfig;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.TransitVlanRepository;
import org.openkilda.persistence.repositories.VxlanRepository;
import org.openkilda.persistence.spi.PersistenceProvider;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.InvalidPathException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;

/**
 * Manages operations with flows.
 */
@Service
public class FlowServiceImpl implements FlowService {
    /**
     * The logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(FlowServiceImpl.class);

    /**
     * when getting the switch rules, we'll ignore cookie filter.
     */
    private static final Long IGNORE_COOKIE_FILTER = 0L;

    @VisibleForTesting
    FlowRepository flowRepository;

    @VisibleForTesting
    TransitVlanRepository transitVlanRepository;

    @VisibleForTesting
    VxlanRepository vxlanRepository;

    /**
     * The kafka topic for the flow topology.
     */
    @Value("#{kafkaTopicsConfig.getFlowTopic()}")
    private String topic;

    /**
     * The kafka topic for the new flow topology.
     */
    @Value("#{kafkaTopicsConfig.getFlowHsTopic()}")
    private String flowHsTopic;

    /**
     * The kafka topic for `nbWorker` topology.
     */
    @Value("#{kafkaTopicsConfig.getTopoNbTopic()}")
    private String nbworkerTopic;

    /**
     * The kafka topic for `ping` topology.
     */
    @Value("#{kafkaTopicsConfig.getPingTopic()}")
    private String pingTopic;

    @Value("${neo4j.uri}")
    private String neoUri;

    @Value("${neo4j.user}")
    private String neoUser;

    @Value("${neo4j.password}")
    private String neoPswd;

    @Autowired
    private FlowMapper flowMapper;

    @Autowired
    private PathMapper pathMapper;

    @Autowired
    private ConnectedDeviceMapper connectedDeviceMapper;

    /**
     * Used to get switch rules.
     */
    @Autowired
    private SwitchService switchService;

    @Autowired
    private MessagingChannel messagingChannel;

    @Autowired
    private CorrelationIdFactory idFactory;

    @PostConstruct
    void init() {
        PersistenceManager persistenceManager = PersistenceProvider.getInstance().createPersistenceManager(
                new ConfigurationProvider() {
                    @SuppressWarnings("unchecked")
                    @Override
                    public <T> T getConfiguration(Class<T> configurationType) {
                        if (configurationType.equals(Neo4jConfig.class)) {
                            return (T) new Neo4jConfig() {
                                @Override
                                public String getUri() {
                                    return neoUri;
                                }

                                @Override
                                public String getLogin() {
                                    return neoUser;
                                }

                                @Override
                                public String getPassword() {
                                    return neoPswd;
                                }

                                @Override
                                public int getConnectionPoolSize() {
                                    return 50;
                                }

                                @Override
                                public String getIndexesAuto() {
                                    return "none";
                                }
                            };
                        } else if (configurationType.equals(NetworkConfig.class)) {
                            return (T) new NetworkConfig() {
                                @Override
                                public int getIslUnstableTimeoutSec() {
                                    return 7200;
                                }

                                @Override
                                public int getIslCostWhenPortDown() {
                                    return 10000;
                                }

                                @Override
                                public int getIslCostWhenUnderMaintenance() {
                                    return 10000;
                                }
                            };
                        } else {
                            throw new UnsupportedOperationException("Unsupported configurationType "
                                    + configurationType);
                        }
                    }
                });
        flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();
        transitVlanRepository = persistenceManager.getRepositoryFactory().createTransitVlanRepository();
        vxlanRepository = persistenceManager.getRepositoryFactory().createVxlanRepository();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<FlowResponsePayload> createFlow(final FlowCreatePayload input) {
        final String correlationId = RequestCorrelationId.getId();
        logger.info("Create flow: {}", input);

        FlowCreateRequest payload;
        try {
            payload = new FlowCreateRequest(new FlowDto(input), input.getDiverseFlowId());
        } catch (IllegalArgumentException e) {
            logger.error("Can not parse arguments: {}", e.getMessage());
            throw new MessageException(correlationId, System.currentTimeMillis(), ErrorType.DATA_INVALID,
                    e.getMessage(), "Can not parse arguments when create flow request");
        }

        CommandMessage request = new CommandMessage(
                payload, System.currentTimeMillis(), correlationId, Destination.WFM);

        return messagingChannel.sendAndGet(topic, request)
                .thenApply(FlowResponse.class::cast)
                .thenApply(FlowResponse::getPayload)
                .thenApply(flowMapper::toFlowResponseOutput);
    }

    @Override
    public CompletableFuture<FlowResponseV2> createFlow(FlowRequestV2 request) {
        logger.info("Processing flow creation: {}", request);

        CommandMessage command = new CommandMessage(flowMapper.toFlowCreateRequest(request),
                System.currentTimeMillis(), RequestCorrelationId.getId(), Destination.WFM);

        return messagingChannel.sendAndGet(flowHsTopic, command)
                .thenApply(FlowResponse.class::cast)
                .thenApply(FlowResponse::getPayload)
                .thenApply(flowMapper::toFlowResponseV2);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<FlowResponsePayload> getFlow(final String id) {
        logger.debug("Get flow request for flow {}", id);

        return getFlowReadResponse(id, RequestCorrelationId.getId())
                .thenApply(flowMapper::toFlowResponseOutput);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<FlowResponsePayload> updateFlow(final FlowUpdatePayload input) {
        final String correlationId = RequestCorrelationId.getId();
        logger.info("Update flow request for flow {}", input.getId());

        FlowUpdateRequest payload;
        try {
            payload = new FlowUpdateRequest(new FlowDto(input), input.getDiverseFlowId());
        } catch (IllegalArgumentException e) {
            logger.error("Can not parse arguments: {}", e.getMessage());
            throw new MessageException(correlationId, System.currentTimeMillis(), ErrorType.DATA_INVALID,
                    e.getMessage(), "Can not parse arguments when update flow request");
        }
        CommandMessage request = new CommandMessage(
                payload, System.currentTimeMillis(), correlationId, Destination.WFM);

        return messagingChannel.sendAndGet(topic, request)
                .thenApply(FlowResponse.class::cast)
                .thenApply(FlowResponse::getPayload)
                .thenApply(flowMapper::toFlowResponseOutput);
    }

    @Override
    public CompletableFuture<FlowResponsePayload> patchFlow(String flowId, FlowPatchDto flowPatchDto) {
        logger.info("Patch flow request for flow {}", flowId);

        FlowDto flowDto = flowMapper.toFlowDto(flowPatchDto);
        flowDto.setFlowId(flowId);
        CommandMessage request = new CommandMessage(new FlowPatchRequest(flowDto), System.currentTimeMillis(),
                RequestCorrelationId.getId());

        return messagingChannel.sendAndGet(nbworkerTopic, request)
                .thenApply(FlowResponse.class::cast)
                .thenApply(FlowResponse::getPayload)
                .thenApply(flowMapper::toFlowResponseOutput);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<List<FlowResponsePayload>> getAllFlows() {
        final String correlationId = RequestCorrelationId.getId();
        logger.debug("Get flows request processing");
        FlowsDumpRequest data = new FlowsDumpRequest();
        CommandMessage request = new CommandMessage(data, System.currentTimeMillis(), correlationId, Destination.WFM);

        return messagingChannel.sendAndGetChunked(topic, request)
                .thenApply(result -> result.stream()
                        .map(FlowReadResponse.class::cast)
                        .map(FlowReadResponse::getPayload)
                        .map(flowMapper::toFlowResponseOutput)
                        .collect(Collectors.toList()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<List<FlowResponsePayload>> deleteAllFlows() {
        CompletableFuture<List<FlowResponsePayload>> result = new CompletableFuture<>();
        logger.warn("Delete all flows request");
        // TODO: Need a getFlowIDs .. since that is all we need
        CompletableFuture<List<FlowResponsePayload>> getFlowsStage = this.getAllFlows();

        getFlowsStage.thenApply(flows -> {
            List<CompletableFuture<?>> deletionRequests = new ArrayList<>();
            for (int i = 0; i < flows.size(); i++) {
                String requestId = idFactory.produceChained(String.valueOf(i));
                FlowPayload flow = flows.get(i);
                deletionRequests.add(sendDeleteFlow(flow.getId(), requestId));
            }
            return deletionRequests;
        }).thenApply(requests -> collectResponses(requests, FlowResponsePayload.class)
                .thenApply(result::complete));

        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<FlowResponsePayload> deleteFlow(final String id) {
        logger.info("Delete flow request for flow: {}", id);
        final String correlationId = RequestCorrelationId.getId();

        return sendDeleteFlow(id, correlationId);
    }

    private CompletableFuture<FlowResponsePayload> sendDeleteFlow(String flowId, String correlationId) {
        CommandMessage request = buildDeleteFlowCommand(flowId, correlationId);

        return messagingChannel.sendAndGet(topic, request)
                .thenApply(FlowResponse.class::cast)
                .thenApply(FlowResponse::getPayload)
                .thenApply(flowMapper::toFlowResponseOutput);
    }

    /**
     * Non-blocking primitive .. just create and send delete request.
     *
     * @return the request
     */
    private CommandMessage buildDeleteFlowCommand(final String id, final String correlationId) {
        FlowDto flow = new FlowDto();
        flow.setFlowId(id);
        FlowDeleteRequest data = new FlowDeleteRequest(flow);
        return new CommandMessage(data, System.currentTimeMillis(), correlationId, Destination.WFM);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<FlowIdStatusPayload> statusFlow(final String id) {
        logger.debug("Flow status request for flow: {}", id);
        return getFlowReadResponse(id, RequestCorrelationId.getId())
                .thenApply(FlowReadResponse::getPayload)
                .thenApply(flowMapper::toFlowIdStatusPayload);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<FlowPathPayload> pathFlow(final String id) {
        logger.debug("Flow path request for flow {}", id);
        final String correlationId = RequestCorrelationId.getId();

        GetFlowPathRequest data = new GetFlowPathRequest(id);
        CommandMessage request = new CommandMessage(data, System.currentTimeMillis(), correlationId);

        return messagingChannel.sendAndGetChunked(nbworkerTopic, request)
                .thenApply(result -> result.stream()
                        .map(GetFlowPathResponse.class::cast)
                        .map(GetFlowPathResponse::getPayload)
                        .collect(Collectors.toList()))
                .thenApply(respList -> buildFlowPathPayload(respList, id));
    }

    private FlowPathPayload buildFlowPathPayload(List<FlowPathDto> paths, String flowId) {
        FlowPathDto askedPathDto = paths.stream().filter(e -> e.getId().equals(flowId)).findAny().get();
        // fill primary flow path
        FlowPathPayload payload = new FlowPathPayload();
        payload.setId(askedPathDto.getId());
        payload.setForwardPath(askedPathDto.getForwardPath());
        payload.setReversePath(askedPathDto.getReversePath());

        if (askedPathDto.getProtectedPath() != null) {
            FlowProtectedPathDto protectedPath = askedPathDto.getProtectedPath();

            payload.setProtectedPath(FlowProtectedPath.builder()
                    .forwardPath(protectedPath.getForwardPath())
                    .reversePath(protectedPath.getReversePath())
                    .build());
        }

        // fill group paths
        if (paths.size() > 1) {
            payload.setDiverseGroupPayload(DiverseGroupPayload.builder()
                    .overlappingSegments(askedPathDto.getSegmentsStats())
                    .otherFlows(mapGroupPaths(paths, flowId, FlowPathDto::isPrimaryPathCorrespondStat))
                    .build());

            if (askedPathDto.getProtectedPath() != null) {
                payload.setDiverseGroupProtectedPayload(DiverseGroupPayload.builder()
                        .overlappingSegments(askedPathDto.getProtectedPath().getSegmentsStats())
                        .otherFlows(mapGroupPaths(paths, flowId, e -> !e.isPrimaryPathCorrespondStat()))
                        .build());
            }
        }
        return payload;
    }

    private List<GroupFlowPathPayload> mapGroupPaths(
            List<FlowPathDto> paths, String flowId, Predicate<FlowPathDto> predicate) {
        return paths.stream()
                .filter(e -> !e.getId().equals(flowId))
                .filter(predicate)
                .map(pathMapper::mapGroupFlowPathPayload)
                .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<BatchResults> unpushFlows(List<FlowInfoData> externalFlows, Boolean propagate,
                                                       Boolean verify) {
        FlowOperation op = (propagate) ? FlowOperation.UNPUSH_PROPAGATE : FlowOperation.UNPUSH;
        // TODO: ADD the VERIFY implementation
        return flowPushUnpush(externalFlows, op);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<BatchResults> pushFlows(List<FlowInfoData> externalFlows,
                                                     Boolean propagate, Boolean verify) {
        FlowOperation op = (propagate) ? FlowOperation.PUSH_PROPAGATE : FlowOperation.PUSH;
        // TODO: ADD the VERIFY implementation
        return flowPushUnpush(externalFlows, op);
    }

    /**
     * Reads {@link BidirectionalFlowDto} flow representation from the Storm.
     *
     * @return the bidirectional flow.
     */
    private CompletableFuture<FlowReadResponse> getFlowReadResponse(String flowId, String correlationId) {
        FlowReadRequest data = new FlowReadRequest(flowId);
        CommandMessage request = new CommandMessage(data, System.currentTimeMillis(), correlationId, Destination.WFM);

        return messagingChannel.sendAndGet(topic, request)
                .thenApply(FlowReadResponse.class::cast);
    }

    /**
     * There are only minor differences between push and unpush .. this utility function helps
     */
    private CompletableFuture<BatchResults> flowPushUnpush(List<FlowInfoData> externalFlows, FlowOperation op) {
        final String correlationId = RequestCorrelationId.getId();
        String collect = externalFlows.stream().map(FlowInfoData::getFlowId).collect(Collectors.joining());
        logger.debug("Flow {}: id: {}", op, collect);
        logger.debug("Size of list: {}", externalFlows.size());
        // First, send them all, then wait for all the responses.
        // Send the command to both Flow Topology and to TE
        List<CompletableFuture<?>> flowRequests = new ArrayList<>();    // used for error reporting, if needed
        for (int i = 0; i < externalFlows.size(); i++) {
            FlowInfoData data = externalFlows.get(i);
            data.setOperation(op);  // <-- this is what determines PUSH / UNPUSH
            String flowCorrelation = correlationId + "-FLOW-" + i;
            InfoMessage flowRequest =
                    new InfoMessage(data, System.currentTimeMillis(), flowCorrelation, Destination.WFM, null);
            flowRequests.add(messagingChannel.sendAndGet(topic, flowRequest));
        }

        FlowState expectedState;
        switch (op) {
            case PUSH:
                expectedState = FlowState.UP;
                break;
            case PUSH_PROPAGATE:
                expectedState = FlowState.IN_PROGRESS;
                break;
            default:
                expectedState = FlowState.DOWN;
        }

        CompletableFuture<List<String>> flowFailures = collectResponses(flowRequests, FlowStatusResponse.class)
                .thenApply(flows ->
                        flows.stream()
                                .map(FlowStatusResponse::getPayload)
                                .filter(payload -> payload.getStatus() != expectedState)
                                .map(payload -> "FAILURE (FlowTopo): Flow " + payload.getId()
                                        + " NOT in " + expectedState
                                        + " state: state = " + payload.getStatus())
                                .collect(Collectors.toList()));

        return flowFailures.thenApply(failures -> {
            BatchResults batchResults = new BatchResults(failures.size(),
                    externalFlows.size() - failures.size(), failures);
            logger.debug("Returned: {}", batchResults);
            return batchResults;
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<FlowResponsePayload> swapFlowPaths(String flowId) {
        final String correlationId = RequestCorrelationId.getId();
        logger.info("Swapping paths for flow : {}", flowId);

        FlowPathSwapRequest payload = new FlowPathSwapRequest(flowId, null);
        CommandMessage request = new CommandMessage(
                payload, System.currentTimeMillis(), correlationId, Destination.WFM);

        return messagingChannel.sendAndGet(topic, request)
                .thenApply(FlowResponse.class::cast)
                .thenApply(FlowResponse::getPayload)
                .thenApply(flowMapper::toFlowResponseOutput);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<FlowReroutePayload> rerouteFlow(String flowId) {
        logger.info("Reroute flow request for flow {}", flowId);
        return reroute(flowId, false);
    }

    @Override
    public CompletableFuture<FlowReroutePayload> syncFlow(String flowId) {
        logger.info("Forced reroute request for flow {}", flowId);
        return reroute(flowId, true);
    }

    private static final class SimpleSwitchRule {
        private SwitchId switchId; // so we don't get lost
        private long cookie;
        private int inPort;
        private int outPort;
        private int inVlan;
        private int tunnelId;
        private int outVlan;
        private Long meterId;
        private long pktCount;   // only set from switch rules, not flow rules
        private long byteCount;  // only set from switch rules, not flow rules
        private String version;

        @Override
        public String toString() {
            return "{sw:" + switchId
                    + ", ck:" + cookie
                    + ", in:" + inPort + "-" + inVlan
                    + ", out:" + outPort + "-" + outVlan
                    + '}';
        }

        /**
         * Will convert from the Flow .. FlowPath format to a series of SimpleSwitchRules,
         */
        private static List<SimpleSwitchRule> convertFlowPath(Flow flow, FlowPath path,
                                                              EncapsulationId encapsulationId) {

            boolean forward = flow.isForward(path);
            final int inPort = forward ? flow.getSrcPort() : flow.getDestPort();
            final int inVlan = forward ? flow.getSrcVlan() : flow.getDestVlan();
            final int outPort = forward ? flow.getDestPort() : flow.getSrcPort();
            final int outVlan = forward ? flow.getDestVlan() : flow.getSrcVlan();

            Long pathCookie = Optional.ofNullable(path.getCookie())
                    .map(Cookie::getValue)
                    .filter(cookie -> !cookie.equals(NumberUtils.LONG_ZERO))
                    .orElse(0L);

            boolean isProtected = Objects.equals(flow.getProtectedForwardPathId(), path.getPathId())
                    || Objects.equals(flow.getProtectedReversePathId(), path.getPathId());

            final List<SimpleSwitchRule> result = new ArrayList<>();
            List<PathSegment> pathSegments = path.getSegments();

            /*
             * Start with Ingress
             */
            if (!isProtected) {
                SimpleSwitchRule rule = new SimpleSwitchRule();
                rule.switchId = path.getSrcSwitch().getSwitchId();
                rule.cookie = path.getCookie().getValue();
                rule.inPort = inPort;
                rule.inVlan = inVlan;
                rule.meterId = Optional.ofNullable(path.getMeterId()).map(MeterId::getValue).orElse(null);

                if (pathSegments.isEmpty()) {
                    // single switch rule.
                    rule.outPort = outPort;
                    rule.outVlan = outVlan;
                } else {
                    // flows with two switches or more will have at least 2 in getPath()
                    rule.outPort = pathSegments.get(0).getSrcPort();
                    if (flow.getEncapsulationType().equals(FlowEncapsulationType.TRANSIT_VLAN)) {
                        rule.outVlan = encapsulationId.getEncapsulationId();
                    } else if (flow.getEncapsulationType().equals(FlowEncapsulationType.VXLAN)) {
                        rule.tunnelId = encapsulationId.getEncapsulationId();
                    }
                }

                result.add(rule);
            }

            /*
             * Now Transits
             *
             * .. only if path is greater than 2. If it is 2, then there are just
             * two switches (no transits).
             */
            if (!pathSegments.isEmpty()) {
                for (int i = 1; i < pathSegments.size(); i++) {
                    // eg .. size 4, means 1 transit .. start at 1,2 .. don't process 3
                    PathSegment inSegment = pathSegments.get(i - 1);

                    SimpleSwitchRule rule = new SimpleSwitchRule();
                    rule.switchId = inSegment.getDestSwitch().getSwitchId();
                    rule.inPort = inSegment.getDestPort();

                    rule.cookie = pathCookie;
                    if (flow.getEncapsulationType().equals(FlowEncapsulationType.TRANSIT_VLAN)) {
                        rule.inVlan = encapsulationId.getEncapsulationId();
                    } else if (flow.getEncapsulationType().equals(FlowEncapsulationType.VXLAN)) {
                        rule.tunnelId = encapsulationId.getEncapsulationId();
                    }
                    //TODO: out vlan is not set for transit flows. Is it correct behavior?
                    //rule.outVlan = flow.getTransitVlan();

                    PathSegment outSegment = pathSegments.get(i);
                    rule.outPort = outSegment.getSrcPort();
                    result.add(rule);
                }

                /*
                 * Now Egress .. only if we have a path. Otherwise it is one switch.
                 */
                SimpleSwitchRule rule = new SimpleSwitchRule();
                rule.switchId = path.getDestSwitch().getSwitchId();
                rule.outPort = outPort;
                rule.outVlan = outVlan;
                if (flow.getEncapsulationType().equals(FlowEncapsulationType.TRANSIT_VLAN)) {
                    rule.inVlan = encapsulationId.getEncapsulationId();
                } else if (flow.getEncapsulationType().equals(FlowEncapsulationType.VXLAN)) {
                    rule.tunnelId = encapsulationId.getEncapsulationId();
                }
                rule.inPort = pathSegments.get(pathSegments.size() - 1).getDestPort();
                rule.cookie = pathCookie;
                result.add(rule);
            }

            return result;
        }


        /**
         * Convert switch rules to simple rules, as much as we can.
         */
        public static List<SimpleSwitchRule> convertSwitchRules(SwitchFlowEntries rules) {
            List<SimpleSwitchRule> result = new ArrayList<>();
            if (rules == null || rules.getFlowEntries() == null) {
                return result;
            }

            for (FlowEntry switchRule : rules.getFlowEntries()) {
                logger.debug("FlowEntry: {}", switchRule);
                SimpleSwitchRule rule = new SimpleSwitchRule();
                rule.switchId = rules.getSwitchId();
                rule.cookie = switchRule.getCookie();
                rule.inPort = NumberUtils.toInt(switchRule.getMatch().getInPort());
                rule.inVlan = NumberUtils.toInt(switchRule.getMatch().getVlanVid());
                rule.tunnelId = Optional.ofNullable(switchRule.getMatch().getTunnelId())
                        .map(Integer::decode)
                        .orElse(NumberUtils.INTEGER_ZERO);
                if (switchRule.getInstructions() != null) {
                    // TODO: What is the right way to get OUT VLAN and OUT PORT?  How does it vary?
                    if (switchRule.getInstructions().getApplyActions() != null) {
                        // The outVlan could be empty. If it is, then pop is?
                        FlowApplyActions applyActions = switchRule.getInstructions().getApplyActions();
                        rule.outVlan = Optional.ofNullable(applyActions.getFieldAction())
                                .filter(action -> "vlan_vid".equals(action.getFieldName()))
                                .map(FlowSetFieldAction::getFieldValue)
                                .map(NumberUtils::toInt)
                                .orElse(NumberUtils.INTEGER_ZERO);
                        rule.outPort = NumberUtils.toInt(applyActions.getFlowOutput());
                        if (rule.tunnelId == NumberUtils.INTEGER_ZERO) {
                            rule.tunnelId = Optional.ofNullable(applyActions.getPushVxlan())
                                    .map(Integer::parseInt)
                                    .orElse(NumberUtils.INTEGER_ZERO);
                        }
                    }

                    rule.meterId = Optional.ofNullable(switchRule.getInstructions().getGoToMeter())
                            .orElse(null);
                }
                rule.pktCount = switchRule.getPacketCount();
                rule.byteCount = switchRule.getByteCount();
                rule.version = switchRule.getVersion();
                result.add(rule);
            }
            return result;
        }

        /**
         * Finds discrepancy between list of expected and actual rules.
         *
         * @param pktCounts If we find the rule, add its pktCounts. Otherwise, add -1.
         * @param byteCounts If we find the rule, add its pktCounts. Otherwise, add -1.
         */
        static List<PathDiscrepancyDto> findDiscrepancy(
                SimpleSwitchRule expected, List<SimpleSwitchRule> possibleActual,
                List<Long> pktCounts, List<Long> byteCounts) {
            List<PathDiscrepancyDto> result = new ArrayList<>();
            SimpleSwitchRule matched = findMatched(expected, possibleActual);

            /*
             * If we haven't matched anything .. then file discrepancy for each field used in match.
             */
            if (matched == null) {
                result.add(new PathDiscrepancyDto(String.valueOf(expected), "all", String.valueOf(expected), ""));
                pktCounts.add(-1L);
                byteCounts.add(-1L);
            } else {
                doMatchCompare(expected, result, matched);
                pktCounts.add(matched.pktCount);
                byteCounts.add(matched.byteCount);
            }

            return result;
        }

        private static void doMatchCompare(SimpleSwitchRule expected, List<PathDiscrepancyDto> result,
                                           SimpleSwitchRule matched) {
            if (matched.cookie != expected.cookie) {
                result.add(new PathDiscrepancyDto(expected.toString(), "cookie",
                        String.valueOf(expected.cookie), String.valueOf(matched.cookie)));
            }
            if (matched.inPort != expected.inPort) {
                result.add(new PathDiscrepancyDto(expected.toString(), "inPort",
                        String.valueOf(expected.inPort), String.valueOf(matched.inPort)));
            }
            if (matched.inVlan != expected.inVlan) {
                result.add(new PathDiscrepancyDto(expected.toString(), "inVlan",
                        String.valueOf(expected.inVlan), String.valueOf(matched.inVlan)));
            }
            if (matched.tunnelId != expected.tunnelId) {
                result.add(new PathDiscrepancyDto(expected.toString(), "tunnelId",
                        String.valueOf(expected.tunnelId), String.valueOf(matched.tunnelId)));
            }
            // FIXME: let's validate in_port output correctly in case of one-switch-port flow.
            // currently for such flow we get 0 after convertion, but the rule has "output: in_port" value.
            if (matched.outPort != expected.outPort
                    && (expected.inPort != expected.outPort || matched.outPort != 0)) {
                result.add(new PathDiscrepancyDto(expected.toString(), "outPort",
                        String.valueOf(expected.outPort), String.valueOf(matched.outPort)));
            }
            if (matched.outVlan != expected.outVlan) {
                result.add(new PathDiscrepancyDto(expected.toString(), "outVlan",
                        String.valueOf(expected.outVlan), String.valueOf(matched.outVlan)));
            }

            //TODO: dumping of meters on OF_12 switches (and earlier) is not implemented yet, so skip them.
            if ((matched.version == null || matched.version.compareTo("OF_12") > 0)
                    && !Objects.equals(matched.meterId, expected.meterId)) {
                result.add(new PathDiscrepancyDto(expected.toString(), "meterId",
                        String.valueOf(expected.meterId), String.valueOf(matched.meterId)));
            }
        }

        private static SimpleSwitchRule findMatched(SimpleSwitchRule expected, List<SimpleSwitchRule> possibleActual) {
            /*
             * Start with trying to match on the cookie.
             */
            SimpleSwitchRule matched = possibleActual.stream()
                    .filter(rule -> rule.cookie != 0 && rule.cookie == expected.cookie)
                    .findFirst()
                    .orElse(null);
            /*
             * If no cookie match, then try inport and invlan
             */
            if (matched == null) {
                matched = possibleActual.stream()
                        .filter(rule -> rule.inPort == expected.inPort && rule.inVlan == expected.inVlan)
                        .findFirst()
                        .orElse(null);
            }
            /*
             * Lastly, if cookie doesn't match, and inport / invlan doesn't, try outport/outvlan
             */
            if (matched == null) {
                matched = possibleActual.stream()
                        .filter(rule -> rule.outPort == expected.outPort && rule.outVlan == expected.outVlan)
                        .findFirst()
                        .orElse(null);
            }
            return matched;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<List<FlowValidationDto>> validateFlow(final String flowId) {
        /*
         * Algorithm:
         * 1) Grab the flow from the database
         * 2) Grab the information off of each switch
         * 3) Do the comparison
         */

        Flow flow = flowRepository.findById(flowId)
                .orElseThrow(() -> new MessageException(RequestCorrelationId.getId(), System.currentTimeMillis(),
                        ErrorType.NOT_FOUND,
                        format("Could not validate flow: Flow %s not found", flowId), "Flow not found"));

        logger.debug("VALIDATE FLOW: Found Flows: {}", flow);

        if (flow.getStatus() == FlowStatus.DOWN) {
            String correlationId = RequestCorrelationId.getId();
            throw new MessageException(correlationId, System.currentTimeMillis(), ErrorType.UNPROCESSABLE_REQUEST,
                    "Could not validate flow", format("Could not validate flow: Flow %s is in DOWN state", flowId));
        }

        /*
         * Since we are getting switch rules, we can use a set.
         */
        if (flow.getForwardPath() == null) {
            throw new InvalidPathException(flowId, "Forward path was not returned.");
        }
        if (flow.getReversePath() == null) {
            throw new InvalidPathException(flowId, "Reverse path was not returned.");
        }

        FlowValidationResources resources = collectFlowPathValidataionResources(
                flow, flow.getForwardPath(), flow.getReversePath());
        resources.merge(collectFlowPathValidataionResources(flow, flow.getReversePath(), flow.getForwardPath()));

        if (flow.getProtectedForwardPath() != null) {
            resources.merge(collectFlowPathValidataionResources(
                    flow, flow.getProtectedForwardPath(), flow.getProtectedReversePath()));
        }
        if (flow.getProtectedReversePath() != null) {
            resources.merge(collectFlowPathValidataionResources(
                    flow, flow.getProtectedReversePath(), flow.getProtectedForwardPath()));
        }

        /*
         * Reality check: we have the flow, and the switch rules. But they are in different formats.
         * *AND* there are a couple of different ways that one may create a switch rule .. so that
         * part needs to be flexible.
         *
         * Given the above, we'll use a flattened / simple mechanism to represent a switch rule.
         * With that class, we can then:
         * 1) use the flow to created the series of expected rules.
         * 2) either convert all switch rules to the flattened structure, or we try to find the
         *    candidate rule, convert it, and then find discrepancies.
         */

        /*
         * Now Walk the list, getting the switch rules, so we can process the comparisons.
         */
        final Map<SwitchId, List<SimpleSwitchRule>> switchRules = new HashMap<>();

        int index = 1;
        List<CompletableFuture<?>> rulesRequests = new ArrayList<>();
        for (SwitchId switchId : resources.getSwitches()) {
            String requestId = idFactory.produceChained(String.valueOf(index++));
            rulesRequests.add(switchService.getRules(switchId, IGNORE_COOKIE_FILTER, requestId));
        }

        return collectResponses(rulesRequests, SwitchFlowEntries.class)
                .thenApply(allEntries -> {
                    int rulesAmount = 0;

                    for (SwitchFlowEntries switchEntries : allEntries) {
                        switchRules.put(switchEntries.getSwitchId(),
                                SimpleSwitchRule.convertSwitchRules(switchEntries));
                        rulesAmount += Optional.ofNullable(switchEntries.getFlowEntries())
                                .map(List::size)
                                .orElse(0);
                    }
                    return rulesAmount;
                })
                .thenApply(totalRules -> compareRules(switchRules, resources.getRules(), flowId, totalRules));
    }

    private List<FlowValidationDto> compareRules(
            Map<SwitchId, List<SimpleSwitchRule>> rulesPerSwitch, List<List<SimpleSwitchRule>> rulesFromDb,
            String flowId, int totalSwitchRules) {

        List<FlowValidationDto> results = new ArrayList<>();
        for (List<SimpleSwitchRule> oneDirection : rulesFromDb) {
            List<PathDiscrepancyDto> discrepancies = new ArrayList<>();
            List<Long> pktCounts = new ArrayList<>();
            List<Long> byteCounts = new ArrayList<>();
            for (SimpleSwitchRule simpleRule : oneDirection) {
                // This is where the comparisons happen.
                discrepancies.addAll(
                        SimpleSwitchRule.findDiscrepancy(simpleRule,
                                rulesPerSwitch.get(simpleRule.switchId),
                                pktCounts, byteCounts
                        ));
            }

            FlowValidationDto result = new FlowValidationDto();
            result.setFlowId(flowId);
            result.setDiscrepancies(discrepancies);
            result.setAsExpected(discrepancies.isEmpty());
            result.setPktCounts(pktCounts);
            result.setByteCounts(byteCounts);
            result.setFlowRulesTotal(oneDirection.size());
            result.setSwitchRulesTotal(totalSwitchRules);
            results.add(result);
        }
        return results;
    }

    @Override
    public CompletableFuture<PingOutput> pingFlow(String flowId, PingInput payload) {
        FlowPingRequest request = new FlowPingRequest(flowId, payload.getTimeoutMillis());

        final String correlationId = RequestCorrelationId.getId();
        CommandMessage message = new CommandMessage(
                request, System.currentTimeMillis(), correlationId, Destination.WFM);

        return messagingChannel.sendAndGet(pingTopic, message)
                .thenApply(FlowPingResponse.class::cast)
                .thenApply(flowMapper::toPingOutput);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<FlowMeterEntries> modifyMeter(String flowId) {
        MeterModifyRequest request = new MeterModifyRequest(flowId);

        final String correlationId = RequestCorrelationId.getId();
        CommandMessage message = new CommandMessage(request, System.currentTimeMillis(), correlationId,
                Destination.WFM);
        return messagingChannel.sendAndGet(topic, message)
                .thenApply(FlowMeterEntries.class::cast);
    }

    @Override
    public CompletableFuture<FlowRerouteResponseV2> rerouteFlowV2(String flowId) {
        logger.info("Processing flow reroute: {}", flowId);

        FlowRerouteRequest payload = new FlowRerouteRequest(flowId, false);
        CommandMessage command = new CommandMessage(
                payload, System.currentTimeMillis(), RequestCorrelationId.getId(), Destination.WFM);

        return messagingChannel.sendAndGet(flowHsTopic, command)
                .thenApply(FlowRerouteResponse.class::cast)
                .thenApply(response ->
                        flowMapper.toRerouteResponseV2(flowId, response.getPayload(), response.isRerouted()));
    }

    private CompletableFuture<FlowReroutePayload> reroute(String flowId, boolean forced) {
        logger.debug("Reroute flow: {}={}, forced={}", FLOW_ID, flowId, forced);
        String correlationId = RequestCorrelationId.getId();
        FlowRerouteRequest payload = new FlowRerouteRequest(flowId, forced);
        CommandMessage command = new CommandMessage(
                payload, System.currentTimeMillis(), correlationId, Destination.WFM);

        return messagingChannel.sendAndGet(topic, command)
                .thenApply(FlowRerouteResponse.class::cast)
                .thenApply(response ->
                        flowMapper.toReroutePayload(flowId, response.getPayload(), response.isRerouted()));
    }

    @Override
    public CompletableFuture<List<FlowEventPayload>> listFlowEvents(String flowId,
                                                                    long timestampFrom,
                                                                    long timestampTo) {
        String correlationId = RequestCorrelationId.getId();
        GetFlowHistoryRequest request = GetFlowHistoryRequest.builder()
                .flowId(flowId)
                .timestampFrom(timestampFrom)
                .timestampTo(timestampTo)
                .build();
        CommandMessage command = new CommandMessage(request, System.currentTimeMillis(), correlationId);
        return messagingChannel.sendAndGet(nbworkerTopic, command)
                .thenApply(FlowHistoryData.class::cast)
                .thenApply(FlowHistoryData::getPayload);
    }

    private EncapsulationId getEncapsulationId(FlowPath flowPath, FlowPath oppositePath,
                                               FlowEncapsulationType encapsulationType) {
        switch (encapsulationType) {
            case TRANSIT_VLAN:
                return transitVlanRepository.findByPathId(flowPath.getPathId(), oppositePath.getPathId())
                        .stream().findAny().orElse(null);
            case VXLAN:
                return vxlanRepository.findByPathId(flowPath.getPathId(), oppositePath.getPathId())
                        .stream().findAny().orElse(null);
            default:
                logger.error("Unexpected value of encapsulation type: {}", encapsulationType);
                throw new MessageException(ErrorType.DATA_INVALID,
                        format("Unexpected value of encapsulation type: %s", encapsulationType),
                        getClass().getName());
        }
    }

    private FlowValidationResources collectFlowPathValidataionResources(
            Flow flow, FlowPath path, FlowPath oppositePath) {
        EncapsulationId encapsulationId = getEncapsulationId(path, oppositePath, flow.getEncapsulationType());
        return new FlowValidationResources(flow, path, encapsulationId);
    }

    private static class FlowValidationResources {
        private final Set<SwitchId> switches = new HashSet<>();
        private final List<List<SimpleSwitchRule>> rules = new ArrayList<>();

        FlowValidationResources(Flow flow, FlowPath path, EncapsulationId encapsulationId) {
            rules.add(SimpleSwitchRule.convertFlowPath(flow, path, encapsulationId));
            switches.add(path.getSrcSwitch().getSwitchId());
            switches.add(path.getDestSwitch().getSwitchId());
            path.getSegments()
                    .forEach(pathSegment -> switches.add(pathSegment.getDestSwitch().getSwitchId()));
        }

        void merge(FlowValidationResources other) {
            switches.addAll(other.getSwitches());
            rules.addAll(other.getRules());
        }

        Set<SwitchId> getSwitches() {
            return switches;
        }

        List<List<SimpleSwitchRule>> getRules() {
            return rules;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<SwapFlowEndpointPayload> swapFlowEndpoint(SwapFlowEndpointPayload input) {
        final String correlationId = RequestCorrelationId.getId();
        logger.info("Swap endpoints for flow {} and {}", input.getFirstFlow().getFlowId(),
                input.getSecondFlow().getFlowId());

        SwapFlowEndpointRequest payload = new SwapFlowEndpointRequest(flowMapper.toSwapFlowDto(input.getFirstFlow()),
                flowMapper.toSwapFlowDto(input.getSecondFlow()));

        CommandMessage request = new CommandMessage(
                payload, System.currentTimeMillis(), correlationId, Destination.WFM);

        return messagingChannel.sendAndGet(topic, request)
                .thenApply(SwapFlowResponse.class::cast)
                .thenApply(response -> new SwapFlowEndpointPayload(
                        flowMapper.toSwapOutput(response.getFirstFlow().getPayload()),
                        flowMapper.toSwapOutput(response.getSecondFlow().getPayload())));
    }

    @Override
    public CompletableFuture<FlowConnectedDevicesResponse> getFlowConnectedDevices(String flowId, String since) {
        logger.info("Get connected devices for flow {} since {}", flowId, since);

        FlowConnectedDeviceRequest request = new FlowConnectedDeviceRequest(flowId, since);

        CommandMessage message = new CommandMessage(
                request, System.currentTimeMillis(), RequestCorrelationId.getId(), Destination.WFM);

        return messagingChannel.sendAndGet(nbworkerTopic, message)
                .thenApply(org.openkilda.messaging.nbtopology.response.FlowConnectedDevicesResponse.class::cast)
                .thenApply(connectedDeviceMapper::toResponse);
    }
}
