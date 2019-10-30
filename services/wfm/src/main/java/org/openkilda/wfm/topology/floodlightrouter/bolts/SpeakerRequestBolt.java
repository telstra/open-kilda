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

import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.flow.InstallFlowForSwitchManagerRequest;
import org.openkilda.messaging.command.flow.ReinstallDefaultFlowForSwitchManagerRequest;
import org.openkilda.messaging.command.flow.RemoveFlowForSwitchManagerRequest;
import org.openkilda.messaging.command.switches.DumpMetersForNbworkerRequest;
import org.openkilda.messaging.command.switches.DumpMetersForSwitchManagerRequest;
import org.openkilda.messaging.command.switches.DumpRulesForNbworkerRequest;
import org.openkilda.messaging.command.switches.DumpRulesForSwitchManagerRequest;
import org.openkilda.messaging.command.switches.GetExpectedDefaultRulesRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.floodlightrouter.Stream;
import org.openkilda.wfm.topology.floodlightrouter.service.RouterUtils;
import org.openkilda.wfm.topology.floodlightrouter.service.SwitchMapping;

import lombok.extern.slf4j.Slf4j;
import org.apache.storm.kafka.bolt.mapper.FieldNameBasedTupleToKafkaMapper;
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
    public void handleInput(Tuple input) throws Exception {
        if (Stream.REGION_NOTIFICATION.equals(input.getSourceStreamId())) {
            updateSwitchMapping((SwitchMapping) input.getValueByField(
                    AbstractTopology.MESSAGE_FIELD));
        } else {
            Message message = (Message) pullRequest(input);
            if (RouterUtils.isBroadcast(message)) {
                for (String region : regions) {
                    proxyRequestToSpeaker(input, region);
                }
            } else {
                SwitchId switchId = RouterUtils.lookupSwitchId(message);
                if (switchId != null) {
                    String region = switchTracker.lookupRegion(switchId);
                    if (region != null) {
                        proxyRequestToSpeaker(input, region);
                    } else {
                        if (message instanceof CommandMessage) {
                            processNotFoundError((CommandMessage) message, switchId, input);
                        }

                    }

                } else {
                    log.error("Unable to lookup region for message: {}", message);
                }
            }
        }
    }

    private void processNotFoundError(CommandMessage commandMessage, SwitchId switchId, Tuple input) {
        String errorDetails = String.format("Switch %s was not found", switchId.toString());
        ErrorData errorData = new ErrorData(ErrorType.NOT_FOUND, errorDetails, errorDetails);
        ErrorMessage errorMessage = new ErrorMessage(errorData, System.currentTimeMillis(),
                commandMessage.getCorrelationId(), null);
        Values values = new Values(commandMessage.getCorrelationId(), errorMessage);
        if (commandMessage.getData() instanceof DumpRulesForNbworkerRequest
                || commandMessage.getData() instanceof DumpMetersForNbworkerRequest) {
            getOutput().emit(Stream.NB_WORKER, input, values);
        } else if (commandMessage.getData() instanceof DumpRulesForSwitchManagerRequest
                || commandMessage.getData() instanceof DumpMetersForSwitchManagerRequest
                || commandMessage.getData() instanceof GetExpectedDefaultRulesRequest
                || commandMessage.getData() instanceof InstallFlowForSwitchManagerRequest
                || commandMessage.getData() instanceof RemoveFlowForSwitchManagerRequest
                || commandMessage.getData() instanceof ReinstallDefaultFlowForSwitchManagerRequest) {
            getOutput().emit(Stream.KILDA_SWITCH_MANAGER, input, values);
        } else {
            log.error("Unable to lookup region for message: {}. switch is not tracked.", commandMessage);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        super.declareOutputFields(outputFieldsDeclarer);
        Fields fields = new Fields(FieldNameBasedTupleToKafkaMapper.BOLT_KEY,
                FieldNameBasedTupleToKafkaMapper.BOLT_MESSAGE);
        outputFieldsDeclarer.declareStream(Stream.NB_WORKER, fields);
        outputFieldsDeclarer.declareStream(Stream.KILDA_SWITCH_MANAGER, fields);
    }
}
