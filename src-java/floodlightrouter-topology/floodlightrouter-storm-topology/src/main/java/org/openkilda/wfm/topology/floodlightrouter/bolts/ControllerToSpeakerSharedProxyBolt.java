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

import org.openkilda.config.KafkaTopicsConfig;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.flow.DeleteMeterRequest;
import org.openkilda.messaging.command.flow.InstallFlowForSwitchManagerRequest;
import org.openkilda.messaging.command.flow.ReinstallDefaultFlowForSwitchManagerRequest;
import org.openkilda.messaging.command.flow.RemoveFlowForSwitchManagerRequest;
import org.openkilda.messaging.command.switches.DumpMetersForNbworkerRequest;
import org.openkilda.messaging.command.switches.DumpMetersForSwitchManagerRequest;
import org.openkilda.messaging.command.switches.DumpMetersRequest;
import org.openkilda.messaging.command.switches.DumpPortDescriptionRequest;
import org.openkilda.messaging.command.switches.DumpRulesForNbworkerRequest;
import org.openkilda.messaging.command.switches.DumpRulesForSwitchManagerRequest;
import org.openkilda.messaging.command.switches.DumpRulesRequest;
import org.openkilda.messaging.command.switches.DumpSwitchPortsDescriptionRequest;
import org.openkilda.messaging.command.switches.GetExpectedDefaultRulesRequest;
import org.openkilda.messaging.command.switches.PortConfigurationRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.topology.floodlightrouter.RegionAwareKafkaTopicSelector;
import org.openkilda.wfm.topology.floodlightrouter.Stream;

import lombok.extern.slf4j.Slf4j;
import org.apache.storm.kafka.bolt.mapper.FieldNameBasedTupleToKafkaMapper;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.Set;

@Slf4j
public class ControllerToSpeakerSharedProxyBolt extends ControllerToSpeakerProxyBolt {
    private final String kafkaNbWorkerTopic;
    private final String kafkaSwitchManagerTopic;
    private final String kafkaNorthboundTopic;

    public ControllerToSpeakerSharedProxyBolt(
            String targetTopic, Set<String> allRegions, KafkaTopicsConfig kafkaTopics) {
        super(targetTopic, allRegions);

        kafkaNbWorkerTopic = kafkaTopics.getTopoNbTopic();
        kafkaSwitchManagerTopic = kafkaTopics.getTopoSwitchManagerTopic();
        kafkaNorthboundTopic = kafkaTopics.getNorthboundTopic();
    }

    @Override
    protected void handleRegionNotFoundError(Object payload, SwitchId switchId) {
        if (payload instanceof CommandMessage) {
            handleRegionNotFoundError((CommandMessage) payload, switchId);
        } else {
            super.handleRegionNotFoundError(payload, switchId);
        }
    }

    private void handleRegionNotFoundError(CommandMessage commandMessage, SwitchId switchId) {
        String errorDetails = String.format("Switch %s not found", switchId.toString());
        ErrorData errorData = new ErrorData(ErrorType.NOT_FOUND, errorDetails, errorDetails);
        ErrorMessage errorMessage = new ErrorMessage(errorData, System.currentTimeMillis(),
                commandMessage.getCorrelationId(), null);

        Tuple input = getCurrentTuple();
        if (commandMessage.getData() instanceof DumpRulesForNbworkerRequest
                || commandMessage.getData() instanceof DumpMetersForNbworkerRequest) {
            // FIXME(surabujin): there is no subscriber on this stream now
            getOutput().emit(Stream.NB_WORKER, input, makeNbWorkerTuple(
                    commandMessage.getCorrelationId(), errorMessage));
        } else if (commandMessage.getData() instanceof DumpRulesForSwitchManagerRequest
                || commandMessage.getData() instanceof DumpMetersForSwitchManagerRequest
                || commandMessage.getData() instanceof GetExpectedDefaultRulesRequest
                || commandMessage.getData() instanceof InstallFlowForSwitchManagerRequest
                || commandMessage.getData() instanceof RemoveFlowForSwitchManagerRequest
                || commandMessage.getData() instanceof ReinstallDefaultFlowForSwitchManagerRequest) {
            getOutput().emit(Stream.KILDA_SWITCH_MANAGER, input, makeSwitchManagerTuple(
                    commandMessage.getCorrelationId(), errorMessage));
        } else if (commandMessage.getData() instanceof DumpSwitchPortsDescriptionRequest
                || commandMessage.getData() instanceof DumpPortDescriptionRequest
                || commandMessage.getData() instanceof DumpRulesRequest
                || commandMessage.getData() instanceof DumpMetersRequest
                || commandMessage.getData() instanceof DeleteMeterRequest
                || commandMessage.getData() instanceof PortConfigurationRequest) {
            getOutput().emit(Stream.NORTHBOUND_REPLY, input, makeNorthboundTuple(
                    commandMessage.getCorrelationId(), errorMessage));
        } else {
            log.error("Unable to lookup region for message: {}. switch is not tracked.", commandMessage);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        super.declareOutputFields(outputFieldsDeclarer);

        Fields fields = new Fields(
                FieldNameBasedTupleToKafkaMapper.BOLT_KEY, FieldNameBasedTupleToKafkaMapper.BOLT_MESSAGE,
                RegionAwareKafkaTopicSelector.FIELD_ID_TOPIC, RegionAwareKafkaTopicSelector.FIELD_ID_REGION);
        outputFieldsDeclarer.declareStream(Stream.NB_WORKER, fields);
        outputFieldsDeclarer.declareStream(Stream.KILDA_SWITCH_MANAGER, fields);
        outputFieldsDeclarer.declareStream(Stream.NORTHBOUND_REPLY, fields);
    }

    private Values makeNbWorkerTuple(String key, Message payload) {
        return makeGenericControllerBolt(key, payload, kafkaNbWorkerTopic);
    }

    private Values makeSwitchManagerTuple(String key, Message payload) {
        return makeGenericControllerBolt(key, payload, kafkaSwitchManagerTopic);
    }

    private Values makeNorthboundTuple(String key, Message payload) {
        return makeGenericControllerBolt(key, payload, kafkaNorthboundTopic);
    }

    private Values makeGenericControllerBolt(String key, Message payload, String topic) {
        return new Values(key, payload, topic, null);
    }
}
