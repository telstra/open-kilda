/* Copyright 2018 Telstra Open Source
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

import static org.openkilda.messaging.Utils.FLOW_ID;
import static org.openkilda.northbound.utils.async.AsyncUtils.collectResponses;

import org.openkilda.config.provider.ConfigurationProvider;
import org.openkilda.messaging.Destination;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.flow.FlowCacheSyncRequest;
import org.openkilda.messaging.command.flow.FlowCreateRequest;
import org.openkilda.messaging.command.flow.FlowDeleteRequest;
import org.openkilda.messaging.command.flow.FlowPingRequest;
import org.openkilda.messaging.command.flow.FlowReadRequest;
import org.openkilda.messaging.command.flow.FlowRerouteRequest;
import org.openkilda.messaging.command.flow.FlowUpdateRequest;
import org.openkilda.messaging.command.flow.FlowsDumpRequest;
import org.openkilda.messaging.command.flow.MeterModifyRequest;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.flow.FlowInfoData;
import org.openkilda.messaging.info.flow.FlowOperation;
import org.openkilda.messaging.info.flow.FlowPingResponse;
import org.openkilda.messaging.info.flow.FlowReadResponse;
import org.openkilda.messaging.info.flow.FlowRerouteResponse;
import org.openkilda.messaging.info.flow.FlowResponse;
import org.openkilda.messaging.info.flow.FlowStatusResponse;
import org.openkilda.messaging.info.meter.FlowMeterEntries;
import org.openkilda.messaging.info.rule.FlowApplyActions;
import org.openkilda.messaging.info.rule.FlowEntry;
import org.openkilda.messaging.info.rule.FlowSetFieldAction;
import org.openkilda.messaging.info.rule.SwitchFlowEntries;
import org.openkilda.messaging.model.BidirectionalFlowDto;
import org.openkilda.messaging.model.FlowDto;
import org.openkilda.messaging.nbtopology.request.FlowPatchRequest;
import org.openkilda.messaging.nbtopology.request.GetFlowPathRequest;
import org.openkilda.messaging.nbtopology.response.GetFlowPathResponse;
import org.openkilda.messaging.payload.flow.DiverseGroupPayload;
import org.openkilda.messaging.payload.flow.FlowCreatePayload;
import org.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.openkilda.messaging.payload.flow.FlowPathPayload;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.messaging.payload.flow.FlowReroutePayload;
import org.openkilda.messaging.payload.flow.FlowState;
import org.openkilda.messaging.payload.flow.FlowUpdatePayload;
import org.openkilda.messaging.payload.flow.GroupFlowPathPayload;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.SwitchId;
import org.openkilda.northbound.converter.FlowMapper;
import org.openkilda.northbound.dto.BatchResults;
import org.openkilda.northbound.dto.flows.FlowPatchDto;
import org.openkilda.northbound.dto.flows.FlowValidationDto;
import org.openkilda.northbound.dto.flows.PathDiscrepancyDto;
import org.openkilda.northbound.dto.flows.PingInput;
import org.openkilda.northbound.dto.flows.PingOutput;
import org.openkilda.northbound.messaging.MessagingChannel;
import org.openkilda.northbound.service.FlowService;
import org.openkilda.northbound.service.SwitchService;
import org.openkilda.northbound.utils.CorrelationIdFactory;
import org.openkilda.northbound.utils.RequestCorrelationId;
import org.openkilda.persistence.Neo4jConfig;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.spi.PersistenceProvider;

import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.InvalidPathException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
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

    private FlowRepository flowRepository;

    /**
     * The kafka topic for the flow topology.
     */
    @Value("#{kafkaTopicsConfig.getFlowTopic()}")
    private String topic;

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
                            };
                        } else {
                            throw new UnsupportedOperationException("Unsupported configurationType "
                                    + configurationType);
                        }
                    }
                });
        flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<FlowPayload> createFlow(final FlowCreatePayload input) {
        final String correlationId = RequestCorrelationId.getId();
        logger.info("Create flow: {}", input);

        FlowCreateRequest payload = new FlowCreateRequest(new FlowDto(input), input.getDiverseFlowId());
        CommandMessage request = new CommandMessage(
                payload, System.currentTimeMillis(), correlationId, Destination.WFM);

        return messagingChannel.sendAndGet(topic, request)
                .thenApply(FlowResponse.class::cast)
                .thenApply(FlowResponse::getPayload)
                .thenApply(flowMapper::toFlowOutput);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<FlowPayload> getFlow(final String id) {
        logger.debug("Get flow request for flow {}", id);

        return getBidirectionalFlow(id, RequestCorrelationId.getId())
                .thenApply(BidirectionalFlowDto::getForward)
                .thenApply(flowMapper::toFlowOutput);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<FlowPayload> updateFlow(final FlowUpdatePayload input) {
        final String correlationId = RequestCorrelationId.getId();
        logger.info("Update flow request for flow {}", input.getId());

        FlowUpdateRequest payload = new FlowUpdateRequest(new FlowDto(input), input.getDiverseFlowId());
        CommandMessage request = new CommandMessage(
                payload, System.currentTimeMillis(), correlationId, Destination.WFM);

        return messagingChannel.sendAndGet(topic, request)
                .thenApply(FlowResponse.class::cast)
                .thenApply(FlowResponse::getPayload)
                .thenApply(flowMapper::toFlowOutput);
    }

    @Override
    public CompletableFuture<FlowPayload> patchFlow(String flowId, FlowPatchDto flowPatchDto) {
        logger.info("Patch flow request for flow {}", flowId);

        FlowDto flowDto = flowMapper.toFlowDto(flowPatchDto);
        flowDto.setFlowId(flowId);
        CommandMessage request = new CommandMessage(new FlowPatchRequest(flowDto), System.currentTimeMillis(),
                RequestCorrelationId.getId());

        return messagingChannel.sendAndGet(nbworkerTopic, request)
                .thenApply(FlowResponse.class::cast)
                .thenApply(FlowResponse::getPayload)
                .thenApply(flowMapper::toFlowOutput);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<List<FlowPayload>> getAllFlows() {
        final String correlationId = RequestCorrelationId.getId();
        logger.debug("Get flows request processing");
        FlowsDumpRequest data = new FlowsDumpRequest();
        CommandMessage request = new CommandMessage(data, System.currentTimeMillis(), correlationId, Destination.WFM);

        return messagingChannel.sendAndGetChunked(topic, request)
                .thenApply(result -> result.stream()
                        .map(FlowReadResponse.class::cast)
                        .map(FlowReadResponse::getPayload)
                        .map(BidirectionalFlowDto::getForward)
                        .map(flowMapper::toFlowOutput)
                        .collect(Collectors.toList()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<List<FlowPayload>> deleteAllFlows() {
        CompletableFuture<List<FlowPayload>> result = new CompletableFuture<>();
        logger.warn("Delete all flows request");
        // TODO: Need a getFlowIDs .. since that is all we need
        CompletableFuture<List<FlowPayload>> getFlowsStage = this.getAllFlows();

        getFlowsStage.thenApply(flows -> {
            List<CompletableFuture<?>> deletionRequests = new ArrayList<>();
            for (int i = 0; i < flows.size(); i++) {
                String requestId = idFactory.produceChained(String.valueOf(i));
                FlowPayload flow = flows.get(i);
                deletionRequests.add(sendDeleteFlow(flow.getId(), requestId));
            }
            return deletionRequests;
        }).thenApply(requests -> collectResponses(requests, FlowPayload.class)
                .thenApply(result::complete));

        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<FlowPayload> deleteFlow(final String id) {
        logger.info("Delete flow request for flow: {}", id);
        final String correlationId = RequestCorrelationId.getId();

        return sendDeleteFlow(id, correlationId);
    }

    private CompletableFuture<FlowPayload> sendDeleteFlow(String flowId, String correlationId) {
        CommandMessage request = buildDeleteFlowCommand(flowId, correlationId);

        return messagingChannel.sendAndGet(topic, request)
                .thenApply(FlowResponse.class::cast)
                .thenApply(FlowResponse::getPayload)
                .thenApply(flowMapper::toFlowOutput);
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
        return getBidirectionalFlow(id, RequestCorrelationId.getId())
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

    private FlowPathPayload buildFlowPathPayload(List<GroupFlowPathPayload> paths, String flowId) {
        GroupFlowPathPayload flowPathPayload = paths.stream().filter(e -> e.getId().equals(flowId)).findAny().get();
        // fill main flow path
        FlowPathPayload payload = new FlowPathPayload();
        payload.setId(flowPathPayload.getId());
        payload.setForwardPath(flowPathPayload.getForwardPath());
        payload.setReversePath(flowPathPayload.getReversePath());

        // fill group paths
        if (paths.size() > 1) {
            DiverseGroupPayload groupPayload = new DiverseGroupPayload();
            groupPayload.setOverlappingSegments(flowPathPayload.getSegmentsStats());
            groupPayload.setOtherFlows(
                    paths.stream().filter(e -> !e.getId().equals(flowId)).collect(Collectors.toList()));

            payload.setDiverseGroupPayload(groupPayload);
        }
        return payload;
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
    private CompletableFuture<BidirectionalFlowDto> getBidirectionalFlow(String flowId, String correlationId) {
        FlowReadRequest data = new FlowReadRequest(flowId);
        CommandMessage request = new CommandMessage(data, System.currentTimeMillis(), correlationId, Destination.WFM);

        return messagingChannel.sendAndGet(topic, request)
                .thenApply(FlowReadResponse.class::cast)
                .thenApply(FlowReadResponse::getPayload);
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
        private int outVlan;
        private Integer meterId;
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
        public static final List<SimpleSwitchRule> convertFlow(Flow flow) {
            /*
             * Start with Ingress
             */
            SimpleSwitchRule rule = new SimpleSwitchRule();
            rule.switchId = flow.getSrcSwitch().getSwitchId();
            rule.cookie = flow.getCookie();
            rule.inPort = flow.getSrcPort();
            rule.inVlan = flow.getSrcVlan();
            rule.meterId = flow.getMeterId();
            List<FlowPath.Node> path = flow.getFlowPath().getNodes();
            // TODO: ensure path is sorted by sequence
            if (path.isEmpty()) {
                // single switch rule.
                rule.outPort = flow.getDestPort();
                rule.outVlan = flow.getDestVlan();
            } else {
                // flows with two switches or more will have at least 2 in getPath()
                rule.outPort = path.get(0).getPortNo();
                rule.outVlan = flow.getTransitVlan();
                // OPTIONAL - for sanity check, we should confirm switch ID and cookie match.
            }
            List<SimpleSwitchRule> result = new ArrayList<>();
            result.add(rule);

            /*
             * Now Transits
             *
             * .. only if path is greater than 2. If it is 2, then there are just
             * two switches (no transits).
             */
            if (path.size() > 2) {
                for (int i = 1; i < path.size() - 1; i = i + 2) {
                    // eg .. size 4, means 1 transit .. start at 1,2 .. don't process 3
                    FlowPath.Node inNode = path.get(i);

                    rule = new SimpleSwitchRule();
                    rule.switchId = inNode.getSwitchId();
                    rule.inPort = inNode.getPortNo();

                    rule.cookie = Optional.ofNullable(inNode.getCookie())
                            .filter(cookie -> !cookie.equals(NumberUtils.LONG_ZERO))
                            .orElse(flow.getCookie());
                    rule.inVlan = flow.getTransitVlan();
                    //TODO: out vlan is not set for transit flows. Is it correct behavior?
                    //rule.outVlan = flow.getTransitVlan();

                    FlowPath.Node outNode = path.get(i + 1);
                    rule.outPort = outNode.getPortNo();
                    result.add(rule);
                }
            }

            /*
             * Now Egress .. only if we have a path. Otherwise it is one switch.
             */
            if (!path.isEmpty()) {
                rule = new SimpleSwitchRule();
                rule.switchId = flow.getDestSwitch().getSwitchId();
                rule.outPort = flow.getDestPort();
                rule.outVlan = flow.getDestVlan();
                rule.inVlan = flow.getTransitVlan();
                rule.inPort = path.get(path.size() - 1).getPortNo();
                rule.cookie = Optional.ofNullable(path.get(path.size() - 1).getCookie())
                        .filter(cookie -> !cookie.equals(NumberUtils.LONG_ZERO))
                        .orElse(flow.getCookie());
                result.add(rule);
            }
            return result;
        }

        /**
         * Convert switch rules to simple rules, as much as we can.
         */
        public static final List<SimpleSwitchRule> convertSwitchRules(SwitchFlowEntries rules) {
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
                    }

                    rule.meterId = Optional.ofNullable(switchRule.getInstructions().getGoToMeter())
                            .map(Long::intValue)
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

        Collection<Flow> flows = flowRepository.findById(flowId);
        logger.debug("VALIDATE FLOW: Found Flows: count = {}", flows.size());
        if (flows.isEmpty()) {
            return null;
        }

        /*
         * Since we are getting switch rules, we can use a set.
         */
        List<List<SimpleSwitchRule>> simpleFlowRules = new ArrayList<>();
        Set<SwitchId> switches = new HashSet<>();
        for (Flow flow : flows) {
            if (flow.getFlowPath() != null) {
                simpleFlowRules.add(SimpleSwitchRule.convertFlow(flow));
                switches.add(flow.getSrcSwitch().getSwitchId());
                switches.add(flow.getDestSwitch().getSwitchId());
                for (FlowPath.Node node : flow.getFlowPath().getNodes()) {
                    switches.add(node.getSwitchId());
                }
            } else {
                throw new InvalidPathException(flowId, "Flow Path was not returned.");
            }
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

        /*)
         * Now Walk the list, getting the switch rules, so we can process the comparisons.
         */
        final Map<SwitchId, List<SimpleSwitchRule>> switchRules = new HashMap<>();

        int index = 1;
        List<CompletableFuture<?>> rulesRequests = new ArrayList<>();
        for (SwitchId switchId : switches) {
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
                .thenApply(totalRules -> compareRules(switchRules, simpleFlowRules, flowId, totalRules));
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
    public void invalidateFlowResourcesCache() {
        final String correlationId = RequestCorrelationId.getId();
        logger.debug("Invalidating Flow Resources Cache.");
        FlowCacheSyncRequest data = new FlowCacheSyncRequest();
        CommandMessage request = new CommandMessage(data, System.currentTimeMillis(), correlationId, Destination.WFM);

        messagingChannel.sendAndGet(topic, request);
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

}
