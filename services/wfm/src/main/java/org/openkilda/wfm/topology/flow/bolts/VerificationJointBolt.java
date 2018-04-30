/*
 * Copyright 2017 Telstra Open Source
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openkilda.wfm.topology.flow.bolts;

import static org.openkilda.messaging.Utils.MAPPER;

import org.openkilda.messaging.Destination;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.flow.FlowVerificationRequest;
import org.openkilda.messaging.command.flow.UniFlowVerificationRequest;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.flow.FlowVerificationErrorCode;
import org.openkilda.messaging.info.flow.FlowVerificationResponse;
import org.openkilda.messaging.info.flow.UniFlowVerificationResponse;
import org.openkilda.messaging.model.BiFlow;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.flow.model.VerificationWaitRecord;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

public class VerificationJointBolt extends AbstractBolt {
    public static final String FIELD_ID_RESPONSE = AbstractTopology.MESSAGE_FIELD;

    public static final String STREAM_SPEAKER_ID = "request";
    public static final Fields STREAM_SPEAKER_FIELDS = AbstractTopology.fieldMessage;
    public static final String STREAM_RESPONSE_ID = "response";
    public static final Fields STREAM_RESPONSE_FIELDS = new Fields(FIELD_ID_RESPONSE);

    private static final Logger logger = LoggerFactory.getLogger(VerificationJointBolt.class);

    private final LinkedList<VerificationWaitRecord> ongoingVerifications = new LinkedList<>();

    @Override
    protected void handleInput(Tuple input) {
        logger.debug("Verification joint - dispatching");
        Object unclassified = input.getValueByField(VerificationBolt.FIELD_ID_OUTPUT);

        if (unclassified instanceof BiFlow) {
            handleRequest(input, (BiFlow) unclassified);
        } else if (unclassified instanceof UniFlowVerificationResponse) {
            handleResponse(input, (UniFlowVerificationResponse) unclassified);
        } else {
            logger.warn(
                    "Unexpected input {} - is topology changes without code change?",
                    unclassified.getClass().getName());
        }
    }

    private void handleRequest(Tuple input, BiFlow biFlow) {
        logger.debug("Handling VERIFICATION request");

        CommandMessage message = fetchInputMessage(input);
        FlowVerificationRequest request = fetchVerificationRequest(message);
        VerificationWaitRecord waitRecord = new VerificationWaitRecord(request, biFlow, message.getCorrelationId());

        List<UniFlowVerificationRequest> pendingRequests = waitRecord.getPendingRequests();
        List<String> jsonMessages = new ArrayList<>(pendingRequests.size());
        try {
            for (UniFlowVerificationRequest uniFlowVerificationRequest : pendingRequests) {
                CommandMessage floodlightMessage = new CommandMessage(
                        uniFlowVerificationRequest, System.currentTimeMillis(), message.getCorrelationId(),
                        Destination.CONTROLLER);
                String s = MAPPER.writeValueAsString(floodlightMessage);
                jsonMessages.add(s);
            }
        } catch (JsonProcessingException e) {
            logger.error("Can't encode {}: {}", UniFlowVerificationRequest.class, e);
            return;
        }

        for (String json : jsonMessages) {
            getOutput().emit(STREAM_SPEAKER_ID, input, new Values(json));
        }

        ongoingVerifications.addLast(waitRecord);
    }

    private void handleResponse(Tuple input, UniFlowVerificationResponse response) {
        logger.debug("Handling VERIFICATION response");

        ListIterator<VerificationWaitRecord> iter = ongoingVerifications.listIterator();

        long currentTime = System.currentTimeMillis();
        while (iter.hasNext()) {
            VerificationWaitRecord waitRecord = iter.next();

            if (waitRecord.isOutdated(currentTime)) {
                iter.remove();
                produceErrorResponse(input, waitRecord);
                continue;
            }

            if (! waitRecord.consumeResponse(response)) {
                continue;
            }
            if (! waitRecord.isFilled()) {
                continue;
            }

            iter.remove();
            produceResponse(input, waitRecord);

            break;
        }
    }

    private CommandMessage fetchInputMessage(Tuple input) {
        Object raw = input.getValueByField(VerificationBolt.FIELD_ID_INPUT);
        if (raw == null) {
            throw new IllegalArgumentException("The message field is empty in input tuple");
        }

        CommandMessage value;
        try {
            value = (CommandMessage)raw;
        } catch (ClassCastException e) {
            throw new IllegalArgumentException(String.format("Can't convert value into Message: %s", e));
        }
        return value;
    }

    private FlowVerificationRequest fetchVerificationRequest(CommandMessage message) {
        FlowVerificationRequest value;
        try {
            value = (FlowVerificationRequest)message.getData();
        } catch (ClassCastException e) {
            throw new IllegalArgumentException(String.format(
                    "Can't fetch flow VERIFICATION request from CommandMessage: %s", e));
        }
        return value;
    }

    private void produceResponse(Tuple input, VerificationWaitRecord waitRecord) {
        FlowVerificationResponse response = waitRecord.produce();
        InfoMessage northboundMessage = new InfoMessage(
                response, System.currentTimeMillis(), waitRecord.getCorrelationId());
        getOutput().emit(STREAM_RESPONSE_ID, input, new Values(northboundMessage));
    }

    private void produceErrorResponse(Tuple input, VerificationWaitRecord waitRecord) {
        waitRecord.fillPendingWithError(FlowVerificationErrorCode.NO_SPEAKER_RESPONSE);
        produceResponse(input, waitRecord);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputManager) {
        outputManager.declareStream(STREAM_SPEAKER_ID, STREAM_SPEAKER_FIELDS);
        outputManager.declareStream(STREAM_RESPONSE_ID, STREAM_RESPONSE_FIELDS);
    }
}
