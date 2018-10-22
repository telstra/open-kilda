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
import org.openkilda.messaging.command.flow.SynchronizeCacheAction;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.messaging.info.flow.FlowCacheSyncResponse;
import org.openkilda.messaging.info.flow.FlowInfoData;
import org.openkilda.messaging.info.flow.FlowOperation;
import org.openkilda.messaging.info.flow.FlowPingResponse;
import org.openkilda.messaging.info.flow.FlowReadResponse;
import org.openkilda.messaging.info.flow.FlowRerouteResponse;
import org.openkilda.messaging.info.flow.FlowResponse;
import org.openkilda.messaging.info.flow.FlowStatusResponse;
import org.openkilda.messaging.info.rule.FlowApplyActions;
import org.openkilda.messaging.info.rule.FlowEntry;
import org.openkilda.messaging.info.rule.FlowSetFieldAction;
import org.openkilda.messaging.info.rule.SwitchFlowEntries;
import org.openkilda.messaging.model.BidirectionalFlow;
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.model.SwitchId;
import org.openkilda.messaging.payload.flow.FlowCacheSyncResults;
import org.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.openkilda.messaging.payload.flow.FlowPathPayload;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.messaging.payload.flow.FlowReroutePayload;
import org.openkilda.messaging.payload.flow.FlowState;
import org.openkilda.northbound.converter.FlowMapper;
import org.openkilda.northbound.dto.BatchResults;
import org.openkilda.northbound.dto.flows.FlowValidationDto;
import org.openkilda.northbound.dto.flows.PathDiscrepancyDto;
import org.openkilda.northbound.dto.flows.PingInput;
import org.openkilda.northbound.dto.flows.PingOutput;
import org.openkilda.northbound.messaging.MessagingChannel;
import org.openkilda.northbound.service.FlowService;
import org.openkilda.northbound.service.SwitchService;
import org.openkilda.northbound.utils.CorrelationIdFactory;
import org.openkilda.northbound.utils.RequestCorrelationId;
import org.openkilda.pce.provider.Auth;
import org.openkilda.pce.provider.AuthNeo4j;
import org.openkilda.pce.provider.NeoDriver;
import org.openkilda.pce.provider.PathComputer;

import org.apache.commons.collections4.ListUtils;
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
    private static final Logger LOGGER = LoggerFactory.getLogger(FlowServiceImpl.class);

    /**
     * when getting the switch rules, we'll ignore cookie filter.
     */
    private static final Long IGNORE_COOKIE_FILTER = 0L;

    private PathComputer pathComputer;
    private Auth pathComputerAuth;

    /**
     * The kafka topic for the flow topology.
     */
    @Value("#{kafkaTopicsConfig.getFlowTopic()}")
    private String topic;

    /**
     * The kafka topic for the topology engine.
     */
    @Value("#{kafkaTopicsConfig.getTopoEngTopic()}")
    private String topoEngTopic;

    /**
     * The kafka topic for `ping` topology.
     */
    @Value("#{kafkaTopicsConfig.getPingTopic()}")
    private String pingTopic;

    @Value("${neo4j.hosts}")
    private String neoHost;

    @Value("${neo4j.user}")
    private String neoUser;

    @Value("${neo4j.pswd}")
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
        pathComputerAuth = new AuthNeo4j(neoHost, neoUser, neoPswd);
        pathComputer = new NeoDriver(pathComputerAuth.getDriver());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<FlowPayload> createFlow(final FlowPayload input) {
        final String correlationId = RequestCorrelationId.getId();
        LOGGER.debug("Create flow: {}", input);

        FlowCreateRequest payload = new FlowCreateRequest(new Flow(input));
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
        logger.debug("Get flow: {}={}", FLOW_ID, id);

        return getBidirectionalFlow(id, RequestCorrelationId.getId())
                .thenApply(BidirectionalFlow::getForward)
                .thenApply(flowMapper::toFlowOutput);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<FlowPayload> updateFlow(final FlowPayload input) {
        final String correlationId = RequestCorrelationId.getId();
        logger.debug("Update flow: {}={}", FLOW_ID, input.getId());

        FlowUpdateRequest payload = new FlowUpdateRequest(new Flow(input));
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
    public CompletableFuture<List<FlowPayload>> getAllFlows() {
        final String correlationId = RequestCorrelationId.getId();
        LOGGER.debug("Get flows request processing");
        FlowsDumpRequest data = new FlowsDumpRequest();
        CommandMessage request = new CommandMessage(data, System.currentTimeMillis(), correlationId, Destination.WFM);

        return messagingChannel.sendAndGetChunked(topic, request)
                .thenApply(result -> result.stream()
                        .map(FlowReadResponse.class::cast)
                        .map(FlowReadResponse::getPayload)
                        .map(BidirectionalFlow::getForward)
                        .map(flowMapper::toFlowOutput)
                        .collect(Collectors.toList()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<List<FlowPayload>> deleteAllFlows() {
        CompletableFuture<List<FlowPayload>> result = new CompletableFuture<>();
        LOGGER.debug("DELETE ALL FLOWS");
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
        logger.debug("Delete flow: {}={}", FLOW_ID, id);
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
     * @return the request
     */
    private CommandMessage buildDeleteFlowCommand(final String id, final String correlationId) {
        Flow flow = new Flow();
        flow.setFlowId(id);
        FlowDeleteRequest data = new FlowDeleteRequest(flow);
        return new CommandMessage(data, System.currentTimeMillis(), correlationId, Destination.WFM);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<FlowIdStatusPayload> statusFlow(final String id) {
        logger.debug("Flow status: {}={}", FLOW_ID, id);
        return getBidirectionalFlow(id, RequestCorrelationId.getId())
                .thenApply(flowMapper::toFlowIdStatusPayload);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<FlowPathPayload> pathFlow(final String id) {
        LOGGER.debug("Flow path: {}={}", FLOW_ID, id);
        return getBidirectionalFlow(id, RequestCorrelationId.getId())
                .thenApply(flowMapper::toFlowPathPayload);
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
     * Reads {@link BidirectionalFlow} flow representation from the Storm.
     * @return the bidirectional flow.
     */
    private CompletableFuture<BidirectionalFlow> getBidirectionalFlow(String flowId, String correlationId) {
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
        LOGGER.debug("Flow {}: id: {}",
                op, externalFlows.stream().map(FlowInfoData::getFlowId).collect(Collectors.joining()));
        LOGGER.debug("Size of list: {}", externalFlows.size());
        // First, send them all, then wait for all the responses.
        // Send the command to both Flow Topology and to TE
        List<CompletableFuture<?>> flowRequests = new ArrayList<>();    // used for error reporting, if needed
        List<CompletableFuture<?>> teRequests = new ArrayList<>();      // used for error reporting, if needed
        for (int i = 0; i < externalFlows.size(); i++) {
            FlowInfoData data = externalFlows.get(i);
            data.setOperation(op);  // <-- this is what determines PUSH / UNPUSH
            String flowCorrelation = correlationId + "-FLOW-" + i;
            InfoMessage flowRequest =
                    new InfoMessage(data, System.currentTimeMillis(), flowCorrelation, Destination.WFM);
            flowRequests.add(messagingChannel.sendAndGet(topic, flowRequest));

            String teCorrelation = correlationId + "-TE-" + i;
            InfoMessage teRequest =
                    new InfoMessage(data, System.currentTimeMillis(), teCorrelation, Destination.TOPOLOGY_ENGINE);
            teRequests.add(messagingChannel.sendAndGet(topoEngTopic, teRequest));
        }

        FlowState expectedState = (op == FlowOperation.PUSH || op == FlowOperation.PUSH_PROPAGATE)
                ? FlowState.UP
                : FlowState.DOWN;

        CompletableFuture<List<String>> flowFailures = collectResponses(flowRequests, FlowStatusResponse.class)
                .thenApply(flows ->
                        flows.stream()
                            .map(FlowStatusResponse::getPayload)
                            .filter(payload -> payload.getStatus() != expectedState)
                            .map(payload -> "FAILURE (FlowTopo): Flow " + payload.getId()
                                    + " NOT in " + expectedState
                                    + " state: state = " + payload.getStatus())
                            .collect(Collectors.toList()));

        CompletableFuture<List<String>> teFailures = collectResponses(teRequests, FlowStatusResponse.class)
                .thenApply(flows ->
                     flows.stream()
                            .map(FlowStatusResponse::getPayload)
                            .filter(payload -> payload.getStatus() != expectedState)
                            .map(payload -> "FAILURE (TE): Flow " + payload.getId()
                                    + " NOT in " + expectedState
                                    + " state: state = " + payload.getStatus())
                            .collect(Collectors.toList()));

        return flowFailures.thenCombine(teFailures, (flowErrors, teErrors) -> {
            BatchResults batchResults = new BatchResults(flowErrors.size() + teErrors.size(),
                    externalFlows.size() - flowErrors.size() + externalFlows.size() - teErrors.size(),
                    ListUtils.sum(flowErrors, teErrors));

            LOGGER.debug("Returned: {}", batchResults);
            return batchResults;
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<FlowReroutePayload> rerouteFlow(String flowId) {
        return reroute(flowId, false);
    }

    @Override
    public CompletableFuture<FlowReroutePayload> syncFlow(String flowId) {
        return reroute(flowId, true);
    }

    private static final class SimpleSwitchRule {
        private SwitchId switchId; // so we don't get lost
        private long cookie;
        private int inPort;
        private int outPort;
        private int inVlan;
        private int outVlan;
        private int meterId;
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
            rule.switchId = flow.getSourceSwitch();
            rule.cookie = flow.getCookie();
            rule.inPort = flow.getSourcePort();
            rule.inVlan = flow.getSourceVlan();
            rule.meterId = flow.getMeterId();
            List<PathNode> path = flow.getFlowPath().getPath();
            // TODO: ensure path is sorted by sequence
            if (path.size() == 0) {
                // single switch rule.
                rule.outPort = flow.getDestinationPort();
                rule.outVlan = flow.getDestinationVlan();
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
                    final PathNode inNode = path.get(i);
                    final PathNode outNode = path.get(i + 1);

                    rule = new SimpleSwitchRule();
                    rule.switchId = inNode.getSwitchId();
                    rule.inPort = inNode.getPortNo();

                    rule.cookie = Optional.ofNullable(inNode.getCookie())
                            .filter(cookie -> !cookie.equals(NumberUtils.LONG_ZERO))
                            .orElse(flow.getCookie());
                    rule.inVlan = flow.getTransitVlan();
                    //TODO: out vlan is not set for transit flows. Is it correct behavior?
                    //rule.outVlan = flow.getTransitVlan();
                    rule.outPort = outNode.getPortNo();
                    result.add(rule);
                }
            }

            /*
             * Now Egress .. only if we have a path. Otherwise it is one switch.
             */
            if (path.size() > 0) {
                rule = new SimpleSwitchRule();
                rule.switchId = flow.getDestinationSwitch();
                rule.outPort = flow.getDestinationPort();
                rule.outVlan = flow.getDestinationVlan();
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
                            .orElse(NumberUtils.INTEGER_ZERO);
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
         * @param pktCounts If we find the rule, add its pktCounts. Otherwise, add -1.
         * @param byteCounts If we find the rule, add its pktCounts. Otherwise, add -1.
         */
        static List<PathDiscrepancyDto> findDiscrepancy(
                SimpleSwitchRule expected, List<SimpleSwitchRule> possibleActual,
                List<Long> pktCounts, List<Long> byteCounts) {
            List<PathDiscrepancyDto> result = new ArrayList<>();

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

            /*
             * If we haven't matched anything .. then file discrepancy for each field used in match.
             */
            if (matched == null) {
                result.add(new PathDiscrepancyDto(String.valueOf(expected), "all", String.valueOf(expected), ""));
                pktCounts.add(-1L);
                byteCounts.add(-1L);
            } else {
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
                if (matched.outPort != expected.outPort) {
                    result.add(new PathDiscrepancyDto(expected.toString(), "outPort",
                            String.valueOf(expected.outPort), String.valueOf(matched.outPort)));
                }
                if (matched.outVlan != expected.outVlan) {
                    result.add(new PathDiscrepancyDto(expected.toString(), "outVlan",
                            String.valueOf(expected.outVlan), String.valueOf(matched.outVlan)));
                }

                //TODO: dumping of meters on OF_12 switches (and earlier) is not implemented yet, so skip them.
                if ((matched.version == null || matched.version.compareTo("OF_12") > 0)
                        && matched.meterId != expected.meterId) {
                    result.add(new PathDiscrepancyDto(expected.toString(), "meterId",
                            String.valueOf(expected.meterId), String.valueOf(matched.meterId)));
                }
                pktCounts.add(matched.pktCount);
                byteCounts.add(matched.byteCount);
            }

            return result;
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

        List<Flow> flows = pathComputer.getFlow(flowId);
        logger.debug("VALIDATE FLOW: Found Flows: count = {}", flows.size());
        if (flows.size() == 0) {
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
                switches.add(flow.getSourceSwitch());
                switches.add(flow.getDestinationSwitch());
                for (PathNode node : flow.getFlowPath().getPath()) {
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
            result.setAsExpected(discrepancies.size() == 0);
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

    @Override
    public CompletableFuture<FlowCacheSyncResults> syncFlowCache(SynchronizeCacheAction syncCacheAction) {
        final String correlationId = RequestCorrelationId.getId();
        LOGGER.debug("Flow cache sync. Action: {}", syncCacheAction);
        FlowCacheSyncRequest data = new FlowCacheSyncRequest(syncCacheAction);
        CommandMessage request = new CommandMessage(data, System.currentTimeMillis(), correlationId, Destination.WFM);

        return messagingChannel.sendAndGet(topic, request)
                .thenApply(FlowCacheSyncResponse.class::cast)
                .thenApply(FlowCacheSyncResponse::getPayload);
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
