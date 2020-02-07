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

package org.openkilda.wfm.topology.stats.bolts;

import static org.openkilda.wfm.AbstractBolt.FIELD_ID_CONTEXT;
import static org.openkilda.wfm.topology.stats.StatsStreamType.CACHE_UPDATE;

import org.openkilda.floodlight.api.request.EgressFlowSegmentInstallRequest;
import org.openkilda.floodlight.api.request.FlowSegmentRequest;
import org.openkilda.floodlight.api.request.IngressFlowSegmentInstallRequest;
import org.openkilda.floodlight.api.request.OneSwitchFlowInstallRequest;
import org.openkilda.messaging.AbstractMessage;
import org.openkilda.messaging.BaseMessage;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.flow.BaseFlow;
import org.openkilda.messaging.command.flow.InstallEgressFlow;
import org.openkilda.messaging.command.flow.InstallIngressFlow;
import org.openkilda.messaging.command.flow.InstallOneSwitchFlow;
import org.openkilda.messaging.command.flow.RemoveFlow;
import org.openkilda.model.Cookie;
import org.openkilda.model.MeterConfig;
import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.topology.stats.MeasurePoint;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

public class CacheFilterBolt extends BaseRichBolt {

    public enum FieldsNames {
        COMMAND,
        SWITCH,
        FLOW,
        COOKIE,
        METER,
        MEASURE_POINT
    }

    public enum Commands {
        UPDATE,
        REMOVE
    }

    public static final Fields fieldsMessageUpdateCache =
            new Fields(
                    FieldsNames.COMMAND.name(),
                    FieldsNames.FLOW.name(),
                    FieldsNames.SWITCH.name(),
                    FieldsNames.COOKIE.name(),
                    FieldsNames.METER.name(),
                    FieldsNames.MEASURE_POINT.name(),
                    FIELD_ID_CONTEXT);


    private static final Logger logger = LoggerFactory.getLogger(CacheFilterBolt.class);

    private OutputCollector outputCollector;


    @Override
    public void prepare(Map map, TopologyContext topologyContext, OutputCollector outputCollector) {
        this.outputCollector = outputCollector;
    }

    @Override
    public void execute(Tuple tuple) {
        try {
            safeExec(tuple);
        } catch (Exception e) {
            logger.error(String.format("Unhandled exception in %s", getClass().getName()), e);
        } finally {
            outputCollector.ack(tuple);
        }
    }

    private void safeExec(Tuple input) {
        String stream = input.getSourceStreamId();
        if (SpeakerRequestDecoderBolt.STREAM_GENERIC_ID.equals(stream)) {
            routeGenericMessage(input);
        } else if (SpeakerRequestDecoderBolt.STREAM_HUB_AND_SPOKE_ID.equals(stream)) {
            routeHubAndSpokeMessage(input);
        } else {
            logger.error(
                    "Unexpected tuple - source:{}, stream:{}, target:{}",
                    input.getSourceComponent(), stream, getClass().getName());
        }
    }

    private void routeGenericMessage(Tuple input) {
        Object message = input.getValueByField(SpeakerRequestDecoderBolt.FIELD_ID_PAYLOAD);
        if (message instanceof BaseMessage) {
            handleSpeakerMessage(input, (BaseMessage) message);
        } else {
            reportNotHandled(input);
        }
    }

    private void routeHubAndSpokeMessage(Tuple input) {
        Object message = input.getValueByField(SpeakerRequestDecoderBolt.FIELD_ID_PAYLOAD);
        if (message instanceof AbstractMessage) {
            handleSpeakerCommand(input, (AbstractMessage) message);
        } else {
            reportNotHandled(input);
        }
    }

    private void handleSpeakerMessage(Tuple tuple, BaseMessage baseMessage) {
        if (baseMessage instanceof CommandMessage) {
            CommandMessage message = (CommandMessage) baseMessage;
            CommandData data = message.getData();
            if (data instanceof InstallIngressFlow) {
                InstallIngressFlow command = (InstallIngressFlow) data;
                logMatchedRecord(command);
                emitUpdateIngress(tuple, command, command.getMeterId());
            } else if (data instanceof InstallEgressFlow) {
                InstallEgressFlow command = (InstallEgressFlow) data;
                logMatchedRecord(command);
                emitUpdateEgress(tuple, command);
            } else if (data instanceof InstallOneSwitchFlow) {
                InstallOneSwitchFlow command = (InstallOneSwitchFlow) data;
                logMatchedRecord(command);
                emitUpdateIngress(tuple, command, command.getMeterId());
                emitUpdateEgress(tuple, command);
            } else if (data instanceof RemoveFlow) {
                RemoveFlow command = (RemoveFlow) data;
                logMatchedRecord(command);
                emitRemove(tuple, command);
            }
        }
    }

    private void handleSpeakerCommand(Tuple input, AbstractMessage request) {
        if (request instanceof FlowSegmentRequest) {
            handleSpeakerCommand(input, (FlowSegmentRequest) request);
        } else if (logger.isDebugEnabled()) {
            logger.debug("Ignore speaker command: {}", request);
        }
    }

    private void handleSpeakerCommand(Tuple input, FlowSegmentRequest rawRequest) {
        if (rawRequest instanceof IngressFlowSegmentInstallRequest) {
            IngressFlowSegmentInstallRequest request = (IngressFlowSegmentInstallRequest) rawRequest;
            logMatchedRecord(request);

            emit(input, Commands.UPDATE, MeasurePoint.INGRESS, request, request.getMeterConfig());
        } else if (rawRequest instanceof OneSwitchFlowInstallRequest) {
            OneSwitchFlowInstallRequest request = (OneSwitchFlowInstallRequest) rawRequest;
            logMatchedRecord(request);

            emit(input, Commands.UPDATE, MeasurePoint.INGRESS, request, request.getMeterConfig());
            emit(input, Commands.UPDATE, MeasurePoint.EGRESS, request);
        } else if (rawRequest instanceof EgressFlowSegmentInstallRequest) {
            logMatchedRecord(rawRequest);
            emit(input, Commands.UPDATE, MeasurePoint.EGRESS, rawRequest);
        } else if (rawRequest.isRemoveRequest()) {
            logMatchedRecord(rawRequest);
            emitRemove(input, rawRequest);
        }
    }

    private void emitUpdateIngress(Tuple input, BaseFlow command, Long meterId) {
        emit(input, Commands.UPDATE, command, meterId, MeasurePoint.INGRESS);
    }

    private void emitUpdateEgress(Tuple input, BaseFlow command) {
        emit(input, Commands.UPDATE, command, null, MeasurePoint.EGRESS);
    }

    private void emitRemove(Tuple tuple, BaseFlow command) {
        emit(tuple, Commands.REMOVE, command, null, null);
    }

    private void emitRemove(Tuple tuple, FlowSegmentRequest request) {
        emit(tuple, Commands.REMOVE, null, request);
    }

    private void emit(Tuple tuple, Commands cacheCommand, MeasurePoint point, FlowSegmentRequest request) {
        emit(tuple, cacheCommand, point, request, null);
    }

    private void emit(
            Tuple tuple, Commands cacheCommand, MeasurePoint point, FlowSegmentRequest request, MeterConfig meter) {
        Cookie cookie = request.getCookie();
        Long meterId = Optional.ofNullable(meter)
                .map(MeterConfig::getId)
                .map(MeterId::getValue)
                .orElse(null);
        String flowId = request.getMetadata().getFlowId();
        emit(tuple, cacheCommand, flowId, request.getSwitchId(), cookie.getValue(), meterId, point);
    }

    private void emit(Tuple tuple, Commands action, BaseFlow command, Long meterId, MeasurePoint point) {
        emit(tuple, action, command.getId(), command.getSwitchId(), command.getCookie(), meterId, point);
    }

    private void emit(Tuple tuple, Commands action, String flowId, SwitchId switchId, Long cookie, Long meterId,
                      MeasurePoint point) {
        CommandContext commandContext = (CommandContext) tuple.getValueByField(FIELD_ID_CONTEXT);
        Values values = new Values(action, flowId, switchId, cookie, meterId, point, commandContext);
        outputCollector.emit(CACHE_UPDATE.name(), tuple, values);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        outputFieldsDeclarer.declareStream(CACHE_UPDATE.name(), fieldsMessageUpdateCache);
    }

    private void logMatchedRecord(BaseFlow flowCommand) {
        logFlowDetails(flowCommand.getClass().getCanonicalName(), flowCommand.getId(), flowCommand.getSwitchId(),
                flowCommand.getCookie());
    }

    private void logMatchedRecord(FlowSegmentRequest request) {
        logFlowDetails(request.getClass().getCanonicalName(), request.getMetadata().getFlowId(), request.getSwitchId(),
                request.getCookie().getValue());
    }

    private void logFlowDetails(String flowCommand, String flowId, SwitchId switchId, Long cookie) {
        logger.debug("Catch {} command flow_id={} sw={} cookie={}", flowCommand, flowId, switchId, cookie);
    }

    private void reportNotHandled(Tuple input) {
        logger.error("Unable to handle input tuple {}", input);
    }
}
