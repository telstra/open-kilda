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

import static java.util.Base64.getEncoder;
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
import org.openkilda.messaging.info.flow.FlowOperation;
import org.openkilda.messaging.info.flow.FlowPathResponse;
import org.openkilda.messaging.info.flow.FlowResponse;
import org.openkilda.messaging.info.flow.FlowStatusResponse;
import org.openkilda.messaging.info.flow.FlowsResponse;
import org.openkilda.messaging.info.flow.FlowCacheSyncResponse;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.payload.flow.FlowCacheSyncResults;
import org.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.openkilda.messaging.payload.flow.FlowPathPayload;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.messaging.info.flow.FlowInfoData;
import org.openkilda.messaging.payload.flow.FlowState;
import org.openkilda.northbound.messaging.MessageConsumer;
import org.openkilda.northbound.messaging.MessageProducer;
import org.openkilda.northbound.service.BatchResults;
import org.openkilda.northbound.service.FlowService;
import org.openkilda.northbound.utils.Converter;

import org.openkilda.northbound.utils.RequestCorrelation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages operations with flows.
 */
@Service
public class FlowServiceImpl implements FlowService {
    /**
     * The logger.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(FlowServiceImpl.class);
    //todo: refactor to use interceptor or custom rest template
    private static final String auth = "kilda:kilda";
    private static final String authHeaderValue = "Basic " + getEncoder().encodeToString(auth.getBytes());


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
    private String pushFlowsUrlBase;
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
        pushFlowsUrlBase = UriComponentsBuilder.fromHttpUrl(topologyEngineRest)
                .pathSegment("api", "v1", "push", "flows").build().toUriString();
        headers = new HttpHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, authHeaderValue);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public FlowPayload createFlow(final FlowPayload flow) {
        final String correlationId = RequestCorrelation.getId();
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
    public FlowPayload deleteFlow(final String id) {
        final String correlationId = RequestCorrelation.getId();
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
    public FlowPayload getFlow(final String id) {
        final String correlationId = RequestCorrelation.getId();
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
    public FlowPayload updateFlow(final FlowPayload flow) {
        final String correlationId = RequestCorrelation.getId();
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
    public List<FlowPayload> getFlows() {
        final String correlationId = RequestCorrelation.getId();
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
    public List<FlowPayload> deleteFlows() {
        String correlationId = RequestCorrelation.getId();
        LOGGER.debug("\n\nDELETE ALL FLOWS: ENTER {}={}\n", CORRELATION_ID, correlationId);
        ArrayList<FlowPayload> result = new ArrayList<>();
        // TODO: Need a getFlowIDs .. since that is all we need
        List<FlowPayload> flows = this.getFlows();

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
    public FlowIdStatusPayload statusFlow(final String id) {
        final String correlationId = RequestCorrelation.getId();
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
    public FlowPathPayload pathFlow(final String id) {
        final String correlationId = RequestCorrelation.getId();
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
    public BatchResults unpushFlows(List<FlowInfoData> externalFlows) {
        return flowPushUnpush(externalFlows, FlowOperation.UNPUSH);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BatchResults pushFlows(List<FlowInfoData> externalFlows) {
        return flowPushUnpush(externalFlows, FlowOperation.PUSH);
    }

    /**
     * There are only minor differences between push and unpush .. this utility function helps
     */
    private BatchResults flowPushUnpush(List<FlowInfoData> externalFlows,
                                        FlowOperation op) {
        final String correlationId = RequestCorrelation.getId();
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
            FlowState expectedState = (op == FlowOperation.PUSH) ? FlowState.UP : FlowState.DOWN;
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
    public FlowPathPayload rerouteFlow(String flowId) {
        final String correlationId = RequestCorrelation.getId();
        Flow flow = new Flow();
        flow.setFlowId(flowId);
        FlowRerouteRequest data = new FlowRerouteRequest(flow, FlowOperation.UPDATE);
        CommandMessage command = new CommandMessage(data, System.currentTimeMillis(), correlationId, Destination.WFM);
        messageConsumer.clear();
        messageProducer.send(topic, command);

        Message message = (Message) messageConsumer.poll(correlationId);
        logger.debug("Got response {}", message);
        FlowPathResponse response = (FlowPathResponse) validateInfoMessage(command, message, correlationId);
        return Converter.buildFlowPathPayloadByFlowPath(flowId, response.getPayload());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FlowCacheSyncResults syncFlowCache() {
        final String correlationId = RequestCorrelation.getId();
        LOGGER.debug("Flow cache sync: {}={}", CORRELATION_ID, correlationId);
        FlowCacheSyncRequest data = new FlowCacheSyncRequest();
        CommandMessage request = new CommandMessage(data, System.currentTimeMillis(), correlationId, Destination.WFM);
        messageConsumer.clear();
        messageProducer.send(topic, request);
        Message message = (Message) messageConsumer.poll(correlationId);
        FlowCacheSyncResponse response = (FlowCacheSyncResponse) validateInfoMessage(request, message, correlationId);
        return response.getPayload();
    }

}
