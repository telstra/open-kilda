/* Copyright 2017 Telstra Open Source
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

import static org.openkilda.messaging.Utils.CORRELATION_ID;

import org.openkilda.messaging.Destination;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.flow.FlowCreateRequest;
import org.openkilda.messaging.command.flow.FlowDeleteRequest;
import org.openkilda.messaging.command.flow.FlowGetRequest;
import org.openkilda.messaging.command.flow.FlowPathRequest;
import org.openkilda.messaging.command.flow.FlowRerouteRequest;
import org.openkilda.messaging.command.flow.FlowStatusRequest;
import org.openkilda.messaging.command.flow.FlowUpdateRequest;
import org.openkilda.messaging.command.flow.FlowsGetRequest;
import org.openkilda.messaging.command.flow.FlowCacheSyncRequest;
import org.openkilda.messaging.command.flow.SynchronizeCacheAction;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.messaging.info.flow.FlowOperation;
import org.openkilda.messaging.info.flow.FlowPathResponse;
import org.openkilda.messaging.info.flow.FlowRerouteResponse;
import org.openkilda.messaging.info.flow.FlowResponse;
import org.openkilda.messaging.info.flow.FlowStatusResponse;
import org.openkilda.messaging.info.flow.FlowsResponse;
import org.openkilda.messaging.info.flow.FlowCacheSyncResponse;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.rule.FlowEntry;
import org.openkilda.messaging.info.rule.SwitchFlowEntries;
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.payload.flow.FlowCacheSyncResults;
import org.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.openkilda.messaging.payload.flow.FlowPathPayload;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.messaging.info.flow.FlowInfoData;
import org.openkilda.messaging.payload.flow.FlowReroutePayload;
import org.openkilda.messaging.payload.flow.FlowState;
import org.openkilda.northbound.dto.FlowValidationDto;
import org.openkilda.northbound.dto.PathDiscrepancyDto;
import org.openkilda.northbound.messaging.MessageConsumer;
import org.openkilda.northbound.messaging.MessageProducer;
import org.openkilda.northbound.service.BatchResults;
import org.openkilda.northbound.service.FlowService;
import org.openkilda.northbound.service.SwitchService;
import org.openkilda.northbound.utils.Converter;

import org.openkilda.pce.provider.Auth;
import org.openkilda.pce.provider.AuthNeo4j;
import org.openkilda.pce.provider.PathComputer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.method.P;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.nio.file.InvalidPathException;
import java.util.*;

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
     * when getting the switch rules, we'll ignore cookie filter
     */
    private static final Long IGNORE_COOKIE_FILTER = 0L;

    private PathComputer pathComputer;
    private Auth pathComputerAuth;

    /**
     * The kafka topic for the flow topology
     */
    @Value("${kafka.flow.topic}")
    private String topic;

    /**
     * The kafka topic for the topology engine
     */
    @Value("${kafka.topo.eng.topic}")
    private String topoEngTopic;


    @Value("${neo4j.hosts}")
    private String neoHost;

    @Value("${neo4j.user}")
    private String neoUser;

    @Value("${neo4j.pswd}")
    private String neoPswd;

    /**
     * Used to get switch rules
     */
    @Autowired
    private SwitchService switchService;

    /**
     * Kafka message consumer.
     */
    @Autowired
    private MessageConsumer messageConsumer;

    /**
     * Kafka message producer.
     */
    @Autowired
    private MessageProducer messageProducer;

    /**
     * Standard variables for calling out to an ENDPOINT
     */
    private HttpHeaders headers;

    /**
     * The TER endpoint
     */
    @Value("${topology.engine.rest.endpoint}")
    private String topologyEngineRest;

    /**
     * Used to call TER
     */
    @Autowired
    private RestTemplate restTemplate;


    @PostConstruct
    void init() {
        pathComputerAuth = new AuthNeo4j(neoHost, neoUser, neoPswd);
        pathComputer = pathComputerAuth.connect();

    }



    /**
     * {@inheritDoc}
     */
    @Override
    public FlowPayload createFlow(final FlowPayload flow, final String correlationId) {
        LOGGER.debug("Create flow: {}={}", CORRELATION_ID, correlationId);
        FlowCreateRequest data = new FlowCreateRequest(Converter.buildFlowByFlowPayload(flow));
        CommandMessage request = new CommandMessage(data, System.currentTimeMillis(), correlationId, Destination.WFM);
        messageConsumer.clear();
        messageProducer.send(topic, request);
        Message message = (Message) messageConsumer.poll(correlationId);
        FlowResponse response = (FlowResponse) validateInfoMessage(request, message, correlationId);
        return Converter.buildFlowPayloadByFlow(response.getPayload());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FlowPayload deleteFlow(final String id, final String correlationId) {
        LOGGER.debug("Delete flow: {}={}", CORRELATION_ID, correlationId);
        messageConsumer.clear();
        CommandMessage request = _sendDeleteFlow(id, correlationId);
        return _deleteFlowRespone(correlationId, request);
    }

    /**
     * Non-blocking primitive .. just create and send delete request
     * @return the request
     */
    private CommandMessage _sendDeleteFlow(final String id, final String correlationId) {
        Flow flow = new Flow();
        flow.setFlowId(id);
        FlowDeleteRequest data = new FlowDeleteRequest(flow);
        CommandMessage request = new CommandMessage(data, System.currentTimeMillis(), correlationId, Destination.WFM);
        messageProducer.send(topic, request);
        return request;
    }

    /**
     * Blocking primitive .. waits for the response .. and then converts to FlowPayload.
     * @return the deleted flow.
     */
    private FlowPayload _deleteFlowRespone(final String correlationId, CommandMessage request) {
        Message message = (Message) messageConsumer.poll(correlationId);
        FlowResponse response = (FlowResponse) validateInfoMessage(request, message, correlationId);
        return Converter.buildFlowPayloadByFlow(response.getPayload());
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public FlowPayload getFlow(final String id, final String correlationId) {
        LOGGER.debug("Get flow: {}={}", CORRELATION_ID, correlationId);
        FlowGetRequest data = new FlowGetRequest(new FlowIdStatusPayload(id, null));
        CommandMessage request = new CommandMessage(data, System.currentTimeMillis(), correlationId, Destination.WFM);
        messageConsumer.clear();
        messageProducer.send(topic, request);
        Message message = (Message) messageConsumer.poll(correlationId);
        FlowResponse response = (FlowResponse) validateInfoMessage(request, message, correlationId);
        return Converter.buildFlowPayloadByFlow(response.getPayload());
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public FlowPayload updateFlow(final FlowPayload flow, final String correlationId) {
        LOGGER.debug("Update flow: {}={}", CORRELATION_ID, correlationId);
        FlowUpdateRequest data = new FlowUpdateRequest(Converter.buildFlowByFlowPayload(flow));
        CommandMessage request = new CommandMessage(data, System.currentTimeMillis(), correlationId, Destination.WFM);
        messageConsumer.clear();
        messageProducer.send(topic, request);
        Message message = (Message) messageConsumer.poll(correlationId);
        FlowResponse response = (FlowResponse) validateInfoMessage(request, message, correlationId);
        return Converter.buildFlowPayloadByFlow(response.getPayload());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<FlowPayload> getFlows(final String correlationId) {
        LOGGER.debug("\n\n\nGet flows: ENTER {}={}\n", CORRELATION_ID, correlationId);
        // TODO: why does FlowsGetRequest use empty FlowIdStatusPayload? Delete if not needed.
        FlowsGetRequest data = new FlowsGetRequest(new FlowIdStatusPayload());
        CommandMessage request = new CommandMessage(data, System.currentTimeMillis(), correlationId, Destination.WFM);
        messageConsumer.clear();
        messageProducer.send(topic, request);
        Message message = (Message) messageConsumer.poll(correlationId);
        FlowsResponse response = (FlowsResponse) validateInfoMessage(request, message, correlationId);
        List<FlowPayload> result = Converter.buildFlowsPayloadByFlows(response.getPayload());
        logger.debug("\nGet flows: EXIT {}={}, num_flows {}\n\n\n", CORRELATION_ID, correlationId, result.size());
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<FlowPayload> deleteFlows(final String correlationId) {
        LOGGER.debug("\n\nDELETE ALL FLOWS: ENTER {}={}\n", CORRELATION_ID, correlationId);
        ArrayList<FlowPayload> result = new ArrayList<>();
        // TODO: Need a getFlowIDs .. since that is all we need
        List<FlowPayload> flows = this.getFlows(correlationId+"-GET");

        messageConsumer.clear();

        // Send all the requests first
        ArrayList<CommandMessage> requests = new ArrayList<>();
        for (int i = 0; i < flows.size(); i++) {
            String cid = correlationId + "-" + i;
            FlowPayload flow = flows.get(i);
            requests.add(_sendDeleteFlow(flow.getId(), cid));
        }
        // Now wait for the responses.
        for (int i = 0; i < flows.size(); i++) {
            String cid = correlationId + "-" + i;
            result.add(_deleteFlowRespone(cid, requests.get(i)));
        }

        LOGGER.debug("\n\nDELETE ALL FLOWS: EXIT {}={}\n", CORRELATION_ID, correlationId);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FlowIdStatusPayload statusFlow(final String id, final String correlationId) {
        LOGGER.debug("Flow status: {}={}", CORRELATION_ID, correlationId);
        FlowStatusRequest data = new FlowStatusRequest(new FlowIdStatusPayload(id, null));
        CommandMessage request = new CommandMessage(data, System.currentTimeMillis(), correlationId, Destination.WFM);
        messageConsumer.clear();
        messageProducer.send(topic, request);
        Message message = (Message) messageConsumer.poll(correlationId);
        FlowStatusResponse response = (FlowStatusResponse) validateInfoMessage(request, message, correlationId);
        return response.getPayload();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FlowPathPayload pathFlow(final String id, final String correlationId) {
        LOGGER.debug("Flow path: {}={}", CORRELATION_ID, correlationId);
        FlowPathRequest data = new FlowPathRequest(new FlowIdStatusPayload(id, null));
        CommandMessage request = new CommandMessage(data, System.currentTimeMillis(), correlationId, Destination.WFM);
        messageConsumer.clear();
        messageProducer.send(topic, request);
        Message message = (Message) messageConsumer.poll(correlationId);
        FlowPathResponse response = (FlowPathResponse) validateInfoMessage(request, message, correlationId);
        return Converter.buildFlowPathPayloadByFlowPath(id, response.getPayload());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BatchResults unpushFlows(List<FlowInfoData> externalFlows, String correlationId,
                                    Boolean propagate, Boolean verify
    ) {
        FlowOperation op = (propagate) ? FlowOperation.UNPUSH_PROPAGATE : FlowOperation.UNPUSH;
        // TODO: ADD the VERIFY implementation
        return flowPushUnpush(externalFlows, correlationId, op);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BatchResults pushFlows(List<FlowInfoData> externalFlows, String correlationId,
                                  Boolean propagate, Boolean verify
    ) {
        FlowOperation op = (propagate) ? FlowOperation.PUSH_PROPAGATE : FlowOperation.PUSH;
        // TODO: ADD the VERIFY implementation
        return flowPushUnpush(externalFlows, correlationId, op);
    }

    /**
     * There are only minor differences between push and unpush .. this utility function helps
     */
    private BatchResults flowPushUnpush(List<FlowInfoData> externalFlows, String correlationId,
                                        FlowOperation op) {
        LOGGER.debug("Flow {}: {}={}", op, CORRELATION_ID, correlationId);
        LOGGER.debug("Size of list: {}", externalFlows.size());
        // First, send them all, then wait for all the responses.
        // Send the command to both Flow Topology and to TE
        messageConsumer.clear();
        ArrayList<InfoMessage> flowRequests = new ArrayList<>();    // used for error reporting, if needed
        ArrayList<InfoMessage> teRequests = new ArrayList<>();      // used for error reporting, if needed
        for (int i = 0; i < externalFlows.size(); i++){
            FlowInfoData data = externalFlows.get(i);
            data.setOperation(op);  // <-- this is what determines PUSH / UNPUSH
            String flowCorrelation = correlationId + "-FLOW-" + i;
            InfoMessage flowRequest = new InfoMessage(data, System.currentTimeMillis(), flowCorrelation, Destination.WFM);
            flowRequests.add(flowRequest);
            messageProducer.send(topic, flowRequest);
            String teCorrelation = correlationId + "-TE-" + i;
            InfoMessage teRequest = new InfoMessage(data, System.currentTimeMillis(), teCorrelation, Destination.TOPOLOGY_ENGINE);
            teRequests.add(teRequest);
            messageProducer.send(topoEngTopic, teRequest);
        }

        int flow_success = 0;
        int flow_failure = 0;
        int te_success = 0;
        int te_failure = 0;
        List<String> msgs = new ArrayList<>();
        msgs.add("Total Flows Received: " + externalFlows.size());

        for (int i = 0; i < externalFlows.size(); i++) {
            String flowCorrelation = correlationId + "-FLOW-" + i;
            String teCorrelation = correlationId + "-TE-" + i;
            FlowState expectedState = (op == FlowOperation.PUSH || op == FlowOperation.PUSH_PROPAGATE) ? FlowState.UP : FlowState.DOWN;
            try {
                Message flowMessage = (Message) messageConsumer.poll(flowCorrelation);
                FlowStatusResponse response = (FlowStatusResponse) validateInfoMessage(flowRequests.get(i), flowMessage, correlationId);
                FlowIdStatusPayload status =  response.getPayload();
                if (status.getStatus() == expectedState) {
                    flow_success++;
                } else {
                    msgs.add("FAILURE (FlowTopo): Flow " + status.getId() +
                            " NOT in " + expectedState +
                            " state: state = " + status.getStatus());
                    flow_failure++;
                }
            } catch (Exception e) {
                msgs.add("EXCEPTION in Flow Topology Response: " + e.getMessage());
                flow_failure++;
            }
            try {
                // TODO: this code block is mostly the same as the previous: consolidate.
                Message teMessage = (Message) messageConsumer.poll(teCorrelation);
                FlowStatusResponse response = (FlowStatusResponse) validateInfoMessage(flowRequests.get(i), teMessage, correlationId);
                FlowIdStatusPayload status =  response.getPayload();
                if (status.getStatus() == expectedState) {
                    te_success++;
                } else {
                    msgs.add("FAILURE (TE): Flow " + status.getId() +
                            " NOT in " + expectedState +
                            " state: state = " + status.getStatus());
                    te_failure++;
                }
            } catch (Exception e) {
                msgs.add("EXCEPTION in Topology Engine Response: " + e.getMessage());
                te_failure++;
            }
        }

        BatchResults result = new BatchResults(
                flow_failure + te_failure,
                flow_success + te_success,
                msgs.stream().toArray(String[]::new));

        LOGGER.debug("Returned: ", result);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FlowReroutePayload rerouteFlow(String flowId, String correlationId) {
        Flow flow = new Flow();
        flow.setFlowId(flowId);
        FlowRerouteRequest data = new FlowRerouteRequest(flow, FlowOperation.UPDATE);
        CommandMessage command = new CommandMessage(data, System.currentTimeMillis(), correlationId, Destination.WFM);
        messageConsumer.clear();
        messageProducer.send(topic, command);

        Message message = (Message) messageConsumer.poll(correlationId);
        logger.debug("Got response {}", message);
        FlowRerouteResponse response = (FlowRerouteResponse) validateInfoMessage(command, message, correlationId);
        return Converter.buildReroutePayload(flowId, response.getPayload(), response.isRerouted());
    }

    private static final class SimpleSwitchRule {
        public String switchId; // so we don't get lost
        public String cookie;
        public String inPort;
        public String outPort;
        public String inVlan;
        public String outVlan;
        public long pktCount;   // only set from switch rules, not flow rules
        public long byteCount;  // only set from switch rules, not flow rules

        @Override
        public String toString() {
            return  "{sw:" + switchId +
                    ", ck:" + cookie +
                    ", in:" + inPort + "-" + inVlan +
                    ", out:" + outPort + "-" + outVlan +
                    '}';
        }

        /**
         * Will convert from the Flow .. FlowPath format to a series of SimpleSwitchRules,
         */
        public static final List<SimpleSwitchRule> convertFlow(Flow flow){
            List<SimpleSwitchRule> result = new ArrayList<>();
            /*
             * Start with Ingress
             */
            SimpleSwitchRule rule = new SimpleSwitchRule();
            rule.switchId = flow.getSourceSwitch();
            rule.cookie = ""+flow.getCookie();
            rule.inPort = ""+flow.getSourcePort();
            rule.inVlan = ""+flow.getSourceVlan();
            List<PathNode> path = flow.getFlowPath().getPath();
            // TODO: ensure path is sorted by sequence
            if (path.size() == 0){
                // single switch rule.
                rule.outPort = ""+flow.getDestinationPort();
                rule.outVlan = ""+flow.getDestinationVlan();
            } else {
                // flows with two switches or more will have at least 2 in getPath()
                rule.outPort = ""+path.get(0).getPortNo();
                rule.outVlan = ""+flow.getTransitVlan();
                // OPTIONAL - for sanity check, we should confirm switch ID and cookie match.
            }
            result.add(rule);

            /*
             * Now Transits
             *
             * .. only if path is greater than 2. If it is 2, then there are just
             * two switches (no transits).
             */
            if (path.size() > 2){
                for (int i = 1; i < path.size()-1; i=i+2) {
                    // eg .. size 4, means 1 transit .. start at 1,2 .. don't process 3
                    PathNode inNode = path.get(i);
                    PathNode outNode = path.get(i+1);

                    rule = new SimpleSwitchRule();
                    rule.switchId = inNode.getSwitchId();
                    rule.inPort = ""+inNode.getPortNo();
                    rule.cookie = ""+inNode.getCookie();
                    if (rule.cookie == null || rule.cookie.length() == 0 || rule.cookie.equals("null"))
                        rule.cookie = ""+flow.getCookie();
                    rule.inVlan = ""+flow.getTransitVlan();
                    rule.outVlan = ""+flow.getTransitVlan();
                    rule.outPort = ""+outNode.getPortNo();
                    result.add(rule);
                }
            }

            /*
             * Now Egress .. only if we have a path. Otherwise it is one switch.
             */
            if (path.size() > 0){
                rule = new SimpleSwitchRule();
                rule.switchId = flow.getDestinationSwitch();
                rule.outPort = ""+flow.getDestinationPort();
                rule.outVlan = ""+flow.getDestinationVlan();
                rule.inVlan = ""+flow.getTransitVlan();
                rule.inPort = ""+path.get(path.size()-1).getPortNo();
                rule.cookie = ""+path.get(path.size()-1).getCookie();
                if (rule.cookie == null || rule.cookie.length() == 0 || rule.cookie.equals("null"))
                    rule.cookie = ""+flow.getCookie();
                result.add(rule);
            }
            return result;
        }

        /**
         * Convert switch rules to simple rules, as much as we can.
         */
        public static final List<SimpleSwitchRule> convertSwitchRules(SwitchFlowEntries rules){
            List<SimpleSwitchRule> result = new ArrayList<>();
            if (rules == null || rules.getFlowEntries() == null)
                return result;

            for (FlowEntry switchRule : rules.getFlowEntries()){
                logger.debug("FlowEntry: {}", switchRule);
                SimpleSwitchRule rule = new SimpleSwitchRule();
                rule.switchId = rules.getSwitchId();
                rule.cookie = ""+switchRule.getCookie();
                rule.inPort = switchRule.getMatch().getInPort();
                rule.inVlan = switchRule.getMatch().getVlanVid();
                if (switchRule.getInstructions() != null){
                    // TODO: What is the right way to get OUT VLAN and OUT PORT?  How does it vary?
                    if (switchRule.getInstructions().getApplyActions() != null) {
                        // The outVlan could be empty. If it is, then pop is?
                        rule.outVlan = switchRule.getInstructions().getApplyActions().getPushVlan();
                        if (rule.outVlan != null && rule.outVlan.equals("0x8100")){
                            rule.outVlan = switchRule.getInstructions().getApplyActions().getFieldAction().getFieldValue();
                        }
                        // Is getFlowOutput() the right method?
                        rule.outPort = switchRule.getInstructions().getApplyActions().getFlowOutput();
                    }
                }
                rule.pktCount = switchRule.getPacketCount();
                rule.byteCount = switchRule.getByteCount();
                result.add(rule);
            }
            return result;
        }

        /**
         * @param pktCounts If we find the rule, add its pktCounts. Otherwise, add -1.
         * @param byteCounts If we find the rule, add its pktCounts. Otherwise, add -1.
         */
        public static final List<PathDiscrepancyDto> findDiscrepancy(
                SimpleSwitchRule expected, List<SimpleSwitchRule> possibleActual,
                List<Long> pktCounts, List<Long> byteCounts) {
            List<PathDiscrepancyDto> result = new ArrayList<>();

            /*
             * Start with trying to match on the cookie.
             */
            SimpleSwitchRule matched = null;
            for (SimpleSwitchRule sr : possibleActual) {
                if (sr.cookie != null && sr.cookie.equals(expected.cookie)) {
                    matched = sr;
                    break;
                }
            }
            /*
             * If no cookie match, then try inport and invlan
             */
            if (matched == null) {
                for (SimpleSwitchRule sr : possibleActual) {

                    if (sr.inPort != null && sr.inPort.equals(expected.inPort) &&
                            sr.inVlan != null && sr.inVlan.equals(expected.inVlan)) {
                        matched = sr;
                        break;
                    }
                }
            }
            /*
             * Lastly, if cookie doesn't match, and inport / invlan doesn't, try outport/outvlan
             */
            if (matched == null) {
                for (SimpleSwitchRule sr : possibleActual) {
                    if (sr.outPort != null && sr.outPort.equals(expected.outPort) &&
                            sr.outVlan != null && sr.outVlan.equals(expected.outVlan)) {
                        matched = sr;
                        break;
                    }
                }
            }

            /*
             * If we haven't matched anything .. then file discrepancy for each field used in match.
             */
            if (matched == null) {
                result.add( new PathDiscrepancyDto(""+expected, "all", ""+expected, "") );
                pktCounts.add(-1L);
                byteCounts.add(-1L);
            } else {
                if (matched.cookie != null && !matched.cookie.equals(expected.cookie))
                    result.add(new PathDiscrepancyDto("" + expected, "cookie", expected.cookie, matched.cookie));
                if (matched.inPort != null && !matched.inPort.equals(expected.inPort))
                    result.add(new PathDiscrepancyDto("" + expected, "inPort", expected.inPort, matched.inPort));
                if (matched.inVlan != null && matched.inVlan.length() > 0) {
                    if (!matched.inVlan.equals(expected.inVlan))
                        result.add(new PathDiscrepancyDto("" + expected, "inVlan", expected.inVlan, matched.inVlan));
                } else {
                    /* If match is empty, but expected isn't, then we have a discrepancy */
                    if (expected.inVlan != null && expected.inVlan.length() > 0 && !expected.inVlan.equals("0")) {
                        result.add(new PathDiscrepancyDto("" + expected, "inVlan", expected.inVlan, matched.inVlan));
                    }
                }
                if (matched.outPort != null && matched.outPort.length() > 0) {
                    if (!matched.outPort.equals(expected.outPort))
                        result.add(new PathDiscrepancyDto("" + expected, "outPort", expected.outPort, matched.outPort));
                }
                if (matched.outVlan != null && matched.outVlan.length() > 0) {
                    if (!matched.outVlan.equals(expected.outVlan))
                        result.add(new PathDiscrepancyDto("" + expected, "outVlan", expected.outVlan, matched.outVlan));
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
    public List<FlowValidationDto> validateFlow(final String flowId, final String correlationId) {
        /*
         * Algorithm:
         * 1) Grab the flow from the database
         * 2) Grab the information off of each switch
         * 3) Do the comparison
         */

        List<Flow> flows = pathComputer.getFlow(flowId);
        if (flows == null)
            return null;

        logger.debug("VALIDATE FLOW: Found Flows: count = {}", flows.size());

        /*
         * Since we are getting switch rules, we can use a set.
         */
        List<List<SimpleSwitchRule>> simpleFlowRules = new ArrayList<>();
        Set<String> switches = new HashSet<>();
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
        Map<String, SwitchFlowEntries> rules = new HashMap<>();
        Map<String, List<SimpleSwitchRule>> simpleRules = new HashMap<>();
        int totalSwitchRules = 0;
        int correlation_iter = 1;
        for (String switchId : switches){
            String corr_id = correlationId+"-"+correlation_iter++;
            SwitchFlowEntries sfe = switchService.getRules(switchId, IGNORE_COOKIE_FILTER, corr_id);
            rules.put(switchId, sfe);
            simpleRules.put(switchId, SimpleSwitchRule.convertSwitchRules(rules.get(switchId)));
            totalSwitchRules += (sfe != null && sfe.getFlowEntries() != null) ? sfe.getFlowEntries().size() : 0;
        }

        /*
         * Now we are ready to compare all the rules.
         */
        List<FlowValidationDto> results = new ArrayList<>();
        for (List<SimpleSwitchRule> oneDirection : simpleFlowRules) {
            List<PathDiscrepancyDto> discrepancies = new ArrayList<>();
            List<Long> pktCounts = new ArrayList<>();
            List<Long> byteCounts = new ArrayList<>();
            for (int i = 0; i < oneDirection.size(); i++) {
                SimpleSwitchRule simpleRule = oneDirection.get(i);
                // This is where the comparisons happen.
                discrepancies.addAll(
                        SimpleSwitchRule.findDiscrepancy(simpleRule,
                                simpleRules.get(simpleRule.switchId),
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

    /**
     * {@inheritDoc}
     */
    @Override
    public FlowCacheSyncResults syncFlowCache(SynchronizeCacheAction syncCacheAction, String correlationId) {
        LOGGER.debug("Flow cache sync: {}={}, SynchronizeCacheAction={}", CORRELATION_ID, correlationId, syncCacheAction);
        FlowCacheSyncRequest data = new FlowCacheSyncRequest(syncCacheAction);
        CommandMessage request = new CommandMessage(data, System.currentTimeMillis(), correlationId, Destination.WFM);
        messageConsumer.clear();
        messageProducer.send(topic, request);
        Message message = (Message) messageConsumer.poll(correlationId);
        FlowCacheSyncResponse response = (FlowCacheSyncResponse) validateInfoMessage(request, message, correlationId);
        return response.getPayload();
    }

}
