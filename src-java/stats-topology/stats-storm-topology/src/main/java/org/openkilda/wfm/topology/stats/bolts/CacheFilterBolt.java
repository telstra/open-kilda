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
import org.openkilda.floodlight.api.request.EgressFlowSegmentRemoveRequest;
import org.openkilda.floodlight.api.request.FlowSegmentRequest;
import org.openkilda.floodlight.api.request.IngressFlowSegmentInstallRequest;
import org.openkilda.floodlight.api.request.IngressFlowSegmentRemoveRequest;
import org.openkilda.floodlight.api.request.OneSwitchFlowInstallRequest;
import org.openkilda.floodlight.api.request.OneSwitchFlowRemoveRequest;
import org.openkilda.floodlight.api.request.TransitFlowSegmentInstallRequest;
import org.openkilda.floodlight.api.request.TransitFlowSegmentRemoveRequest;
import org.openkilda.messaging.AbstractMessage;
import org.openkilda.messaging.BaseMessage;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.flow.BaseFlow;
import org.openkilda.messaging.command.flow.BaseInstallFlow;
import org.openkilda.messaging.command.flow.InstallEgressFlow;
import org.openkilda.messaging.command.flow.InstallIngressFlow;
import org.openkilda.messaging.command.flow.InstallOneSwitchFlow;
import org.openkilda.messaging.command.flow.InstallTransitFlow;
import org.openkilda.messaging.command.flow.RemoveFlow;
import org.openkilda.messaging.command.switches.DeleteRulesCriteria;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.MeterConfig;
import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.topology.stats.MeasurePoint;
import org.openkilda.wfm.topology.stats.MeasurePointKey;

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
        MEASURE_POINT_TYPE,
        MEASURE_POINT_KEY
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
                    FieldsNames.MEASURE_POINT_TYPE.name(),
                    FieldsNames.MEASURE_POINT_KEY.name(),
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
                MeasurePointKey measurePointKey = MeasurePointKey.buildForIngressSwitchRule(command.getSwitchId(),
                        command.getInputPort(), command.getInputVlanId(), command.getInputInnerVlanId(),
                        command.getOutputPort());
                emitUpdate(tuple, command, command.getMeterId(), MeasurePoint.INGRESS, measurePointKey);
            } else if (data instanceof InstallEgressFlow) {
                InstallEgressFlow command = (InstallEgressFlow) data;
                logMatchedRecord(command);
                MeasurePointKey measurePointKey = MeasurePointKey.buildForEgressSwitchRule(command.getSwitchId(),
                        command.getInputPort(),
                        command.getOutputPort(), command.getOutputVlanId(), command.getOutputInnerVlanId());
                emitUpdate(tuple, command, MeasurePoint.EGRESS, measurePointKey);
            } else if (data instanceof InstallOneSwitchFlow) {
                InstallOneSwitchFlow command = (InstallOneSwitchFlow) data;
                logMatchedRecord(command);
                MeasurePointKey measurePointKey = MeasurePointKey.buildForOneSwitchRule(command.getSwitchId(),
                        command.getInputPort(), command.getInputVlanId(), command.getInputInnerVlanId(),
                        command.getOutputPort(), command.getOutputVlanId(), command.getOutputInnerVlanId());
                emitUpdate(tuple, command, command.getMeterId(), MeasurePoint.ONE_SWITCH, measurePointKey);
            } else if (data instanceof InstallTransitFlow) {
                InstallTransitFlow command = (InstallTransitFlow) data;
                logMatchedRecord(command);
                MeasurePointKey measurePointKey = MeasurePointKey.buildForSwitchRule(command.getSwitchId(),
                        command.getInputPort(), command.getOutputPort());
                emitUpdate(tuple, command, MeasurePoint.TRANSIT, measurePointKey);
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
            FlowEndpoint endpoint = request.getEndpoint();
            emitUpdate(input, request, request.getMeterConfig(), MeasurePoint.INGRESS,
                    MeasurePointKey.buildForIngressSwitchRule(request.getSwitchId(),
                            endpoint.getPortNumber(), endpoint.getOuterVlanId(), endpoint.getInnerVlanId(),
                            request.getIslPort()));
        } else if (rawRequest instanceof OneSwitchFlowInstallRequest) {
            OneSwitchFlowInstallRequest request = (OneSwitchFlowInstallRequest) rawRequest;
            logMatchedRecord(request);
            FlowEndpoint endpoint = request.getEndpoint();
            FlowEndpoint egEndpoint = request.getEgressEndpoint();
            emitUpdate(input, request, request.getMeterConfig(), MeasurePoint.ONE_SWITCH,
                    MeasurePointKey.buildForOneSwitchRule(request.getSwitchId(),
                            endpoint.getPortNumber(), endpoint.getOuterVlanId(), endpoint.getInnerVlanId(),
                            egEndpoint.getPortNumber(), egEndpoint.getOuterVlanId(), egEndpoint.getInnerVlanId()));
        } else if (rawRequest instanceof EgressFlowSegmentInstallRequest) {
            EgressFlowSegmentInstallRequest request = (EgressFlowSegmentInstallRequest) rawRequest;
            logMatchedRecord(request);
            FlowEndpoint egEndpoint = request.getEndpoint();
            emitUpdate(input, request, MeasurePoint.EGRESS,
                    MeasurePointKey.buildForEgressSwitchRule(request.getSwitchId(), request.getIslPort(),
                            egEndpoint.getPortNumber(), egEndpoint.getOuterVlanId(), egEndpoint.getInnerVlanId()));
        } else if (rawRequest instanceof TransitFlowSegmentInstallRequest) {
            TransitFlowSegmentInstallRequest request = (TransitFlowSegmentInstallRequest) rawRequest;
            logMatchedRecord(request);
            emitUpdate(input, request, MeasurePoint.TRANSIT,
                    MeasurePointKey.buildForSwitchRule(request.getSwitchId(), request.getIngressIslPort(),
                            request.getEgressIslPort()));
        } else if (rawRequest instanceof IngressFlowSegmentRemoveRequest) {
            IngressFlowSegmentRemoveRequest request = (IngressFlowSegmentRemoveRequest) rawRequest;
            logMatchedRecord(request);

            FlowEndpoint endpoint = request.getEndpoint();
            emitRemove(input, request, MeasurePoint.INGRESS,
                    MeasurePointKey.buildForIngressSwitchRule(request.getSwitchId(),
                            endpoint.getPortNumber(), endpoint.getOuterVlanId(), endpoint.getInnerVlanId(),
                            request.getIslPort()));
        } else if (rawRequest instanceof OneSwitchFlowRemoveRequest) {
            OneSwitchFlowRemoveRequest request = (OneSwitchFlowRemoveRequest) rawRequest;
            logMatchedRecord(request);
            FlowEndpoint endpoint = request.getEndpoint();
            FlowEndpoint egEndpoint = request.getEgressEndpoint();
            emitRemove(input, request, MeasurePoint.ONE_SWITCH,
                    MeasurePointKey.buildForOneSwitchRule(request.getSwitchId(),
                            endpoint.getPortNumber(), endpoint.getOuterVlanId(), endpoint.getInnerVlanId(),
                            egEndpoint.getPortNumber(), egEndpoint.getOuterVlanId(), egEndpoint.getInnerVlanId()));
        } else if (rawRequest instanceof EgressFlowSegmentRemoveRequest) {
            EgressFlowSegmentRemoveRequest request = (EgressFlowSegmentRemoveRequest) rawRequest;
            logMatchedRecord(request);
            FlowEndpoint egEndpoint = request.getEndpoint();
            emitRemove(input, request, MeasurePoint.EGRESS,
                    MeasurePointKey.buildForEgressSwitchRule(request.getSwitchId(), request.getIslPort(),
                            egEndpoint.getPortNumber(), egEndpoint.getOuterVlanId(), egEndpoint.getInnerVlanId()));
        } else if (rawRequest instanceof TransitFlowSegmentRemoveRequest) {
            TransitFlowSegmentRemoveRequest request = (TransitFlowSegmentRemoveRequest) rawRequest;
            logMatchedRecord(request);
            emitRemove(input, request, MeasurePoint.TRANSIT,
                    MeasurePointKey.buildForSwitchRule(request.getSwitchId(), request.getIngressIslPort(),
                            request.getEgressIslPort()));
        }
    }

    private void emitUpdate(Tuple input, BaseInstallFlow command, Long meterId, MeasurePoint measurePointType,
                            MeasurePointKey measurePointKey) {
        emit(input, Commands.UPDATE, command, meterId, measurePointType, measurePointKey);
    }

    private void emitUpdate(Tuple input, BaseInstallFlow command, MeasurePoint measurePointType,
                            MeasurePointKey measurePointKey) {
        emitUpdate(input, command, null, measurePointType, measurePointKey);
    }

    private void emitUpdate(Tuple tuple, FlowSegmentRequest request, MeterConfig meter, MeasurePoint measurePointType,
                            MeasurePointKey measurePointKey) {
        emit(tuple, Commands.UPDATE, request, meter, measurePointType, measurePointKey);
    }

    private void emitUpdate(Tuple tuple, FlowSegmentRequest request, MeasurePoint measurePointType,
                            MeasurePointKey measurePointKey) {
        emitUpdate(tuple, request, null, measurePointType, measurePointKey);
    }

    private void emitRemove(Tuple tuple, FlowSegmentRequest request, MeasurePoint measurePointType,
                            MeasurePointKey measurePointKey) {
        emit(tuple, Commands.REMOVE, request, null, measurePointType, measurePointKey);
    }

    private void emitRemove(Tuple tuple, RemoveFlow command) {
        MeasurePointKey measurePointKey = null;
        DeleteRulesCriteria deleteCriteria = command.getCriteria();
        if (deleteCriteria != null && deleteCriteria.getInPort() != null && deleteCriteria.getOutPort() != null) {
            measurePointKey = MeasurePointKey.buildForSwitchRule(command.getSwitchId(), deleteCriteria.getInPort(),
                    deleteCriteria.getOutPort());
        }
        emit(tuple, Commands.REMOVE, command, null, null, measurePointKey);
    }

    private void emit(Tuple tuple, Commands action, BaseFlow command, Long meterId, MeasurePoint measurePointType,
                      MeasurePointKey measurePointKey) {
        emit(tuple, action, command.getId(), command.getSwitchId(), command.getCookie(), meterId, measurePointType,
                measurePointKey);
    }

    private void emit(Tuple tuple, Commands action, FlowSegmentRequest request, MeterConfig meter,
                      MeasurePoint measurePointType, MeasurePointKey measurePointKey) {
        Cookie cookie = request.getCookie();
        Long meterId = Optional.ofNullable(meter)
                .map(MeterConfig::getId)
                .map(MeterId::getValue)
                .orElse(null);
        String flowId = request.getMetadata().getFlowId();
        emit(tuple, action, flowId, request.getSwitchId(), cookie.getValue(), meterId, measurePointType,
                measurePointKey);
    }

    private void emit(Tuple tuple, Commands action, String flowId, SwitchId switchId, Long cookie, Long meterId,
                      MeasurePoint measurePointType, MeasurePointKey measurePointKey) {
        CommandContext commandContext = (CommandContext) tuple.getValueByField(FIELD_ID_CONTEXT);
        Values values = new Values(action, flowId, switchId, cookie, meterId, measurePointType, measurePointKey,
                commandContext);
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
