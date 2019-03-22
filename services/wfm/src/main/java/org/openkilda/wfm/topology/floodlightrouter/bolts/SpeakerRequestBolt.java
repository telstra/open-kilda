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

package org.openkilda.wfm.topology.floodlightrouter.bolts;

import static org.openkilda.messaging.Utils.MAPPER;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.switches.DumpRulesForSwitchManagerRequest;
import org.openkilda.messaging.command.switches.ValidateRulesRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.floodlightrouter.Stream;
import org.openkilda.wfm.topology.floodlightrouter.service.RouterUtils;
import org.openkilda.wfm.topology.floodlightrouter.service.SwitchMapping;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.Set;

@Slf4j
public class SpeakerRequestBolt extends RequestBolt {

    public SpeakerRequestBolt(String outputStream, Set<String> regions) {
        super(outputStream, regions);
    }

    @Override
    public void execute(Tuple input) {
        try {
            if (Stream.REGION_NOTIFICATION.equals(input.getSourceStreamId())) {
                updateSwitchMapping((SwitchMapping) input.getValueByField(
                        AbstractTopology.MESSAGE_FIELD));
            } else {
                String json = pullRequest(input);
                Message message = MAPPER.readValue(json, Message.class);
                if (RouterUtils.isBroadcast(message)) {
                    for (String region : regions) {
                        proxyRequestToSpeaker(input, region);
                    }
                } else {
                    SwitchId switchId = RouterUtils.lookupSwitchIdInCommandMessage(message);
                    if (switchId != null) {
                        String region = switchTracker.lookupRegion(switchId);
                        if (region != null) {
                            proxyRequestToSpeaker(input, region);
                        } else {
                            if (message instanceof CommandMessage) {
                                processNotFoundError((CommandMessage) message, switchId, input, json);
                            }

                        }

                    } else {
                        log.error("Unable to lookup region for message: {}", json);
                    }
                }
            }
        } catch (Exception e) {
            log.error(String.format("Unhandled exception in %s", getClass().getName()), e);
        } finally {
            outputCollector.ack(input);
        }
    }

    private void processNotFoundError(CommandMessage message, SwitchId switchId, Tuple input,
                                      String json) throws JsonProcessingException {
        CommandMessage commandMessage = (CommandMessage) message;

        String errorDetails = String.format("Switch %s was not found", switchId.toString());
        ErrorData errorData = new ErrorData(ErrorType.NOT_FOUND, errorDetails, errorDetails);
        ErrorMessage errorMessage = new ErrorMessage(errorData, System.currentTimeMillis(),
                message.getCorrelationId(), null);
        String errorJson = MAPPER.writeValueAsString(errorMessage);
        Values values = new Values(errorJson);
        if (commandMessage.getData() instanceof ValidateRulesRequest) {
            outputCollector.emit(Stream.NORTHBOUND_REPLY, input, values);
        } else if (commandMessage.getData() instanceof DumpRulesForSwitchManagerRequest) {
            outputCollector.emit(Stream.NB_WORKER, input, values);
        } else {
            log.error("Unable to lookup region for message: {}. switch is not tracked.", json);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        super.declareOutputFields(outputFieldsDeclarer);
        Fields fields = new Fields(AbstractTopology.MESSAGE_FIELD);
        outputFieldsDeclarer.declareStream(Stream.NORTHBOUND_REPLY, fields);
        outputFieldsDeclarer.declareStream(Stream.NB_WORKER, fields);
    }
}
