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

package org.openkilda.wfm.topology.applications.bolt;

import static java.lang.String.format;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.MessageData;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.flow.FlowAddApplicationRequest;
import org.openkilda.messaging.command.flow.FlowEnabledApplicationsReadRequest;
import org.openkilda.messaging.command.flow.FlowRemoveApplicationRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.flow.FlowEnabledApplicationsResponse;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.topology.applications.AppsTopology.ComponentId;
import org.openkilda.wfm.topology.applications.service.AppsManagerService;
import org.openkilda.wfm.topology.utils.MessageKafkaTranslator;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.List;

public class AppsManager extends AbstractBolt {
    public static final String BOLT_ID = ComponentId.APPS_MANAGER.toString();

    public static final String FIELD_ID_KEY = MessageKafkaTranslator.KEY_FIELD;
    public static final String FIELD_ID_PAYLOAD = MessageKafkaTranslator.FIELD_ID_PAYLOAD;

    public static final Fields STREAM_FIELDS = new Fields(FIELD_ID_KEY, FIELD_ID_PAYLOAD, FIELD_ID_CONTEXT);

    private final PersistenceManager persistenceManager;

    private transient AppsManagerService service;

    public AppsManager(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    @Override
    protected void init() {
        service = new AppsManagerService(persistenceManager);
    }

    @Override
    protected void handleInput(Tuple input) throws Exception {
        String source = input.getSourceComponent();
        if (ComponentId.APPS_SPOUT.toString().equals(source)) {
            processInput(input);
        } else {
            unhandledInput(input);
        }
    }

    private void processInput(Tuple input) throws PipelineException {
        Message message = pullValue(input, FIELD_ID_PAYLOAD, Message.class);
        try {
            processMessage(message);
        } catch (Exception e) {
            log.error("Unhandled exception", e);
            emitErrorMessage(ErrorType.INTERNAL_ERROR, e.getMessage());
        }
    }

    private void processMessage(Message message) {
        if (message instanceof CommandMessage) {
            processCommandData(((CommandMessage) message).getData());
        } else {
            throw new UnsupportedOperationException(format("Unexpected message type \"%s\"", message.getClass()));
        }
    }

    private void processCommandData(CommandData payload) {
        String flowId;
        List<String> applications;
        if (payload instanceof FlowEnabledApplicationsReadRequest) {
            FlowEnabledApplicationsReadRequest readRequest = (FlowEnabledApplicationsReadRequest) payload;
            flowId = readRequest.getFlowId();
            applications = service.getEnabledFlowApplications(flowId);
        } else if (payload instanceof FlowAddApplicationRequest) {
            FlowAddApplicationRequest addRequest = (FlowAddApplicationRequest) payload;
            flowId = addRequest.getFlowId();
            applications = service.addFlowApplication(flowId, addRequest.getApplication());
        } else if (payload instanceof FlowRemoveApplicationRequest) {
            FlowRemoveApplicationRequest removeRequest = (FlowRemoveApplicationRequest) payload;
            flowId = removeRequest.getFlowId();
            applications = service.removeFlowApplication(flowId, removeRequest.getApplication());
        } else {
            throw new UnsupportedOperationException(format("Unexpected message payload \"%s\"", payload.getClass()));
        }

        FlowEnabledApplicationsResponse response = new FlowEnabledApplicationsResponse(flowId, applications);
        emitNorthboundResponse(response);
    }

    private void emitErrorMessage(ErrorType errorType, String errorMessage) {
        ErrorData errorData = new ErrorData(errorType, errorMessage, "Error in the Apps Manager");
        emitNorthboundResponse(errorData);
    }

    private void emitNorthboundResponse(MessageData payload) {
        String key = getCommandContext().getCorrelationId();
        emit(getCurrentTuple(), makeNorthboundTuple(key, payload));
    }

    private Values makeNorthboundTuple(String key, MessageData payload) {
        return new Values(key, payload, getCommandContext());
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer streamManager) {
        streamManager.declare(STREAM_FIELDS);
    }
}
