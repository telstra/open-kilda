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

package org.openkilda.wfm.topology.floodlightrouter.bolts;

import static org.openkilda.messaging.Utils.MAPPER;

import org.openkilda.messaging.AliveRequest;
import org.openkilda.messaging.AliveResponse;
import org.openkilda.messaging.Destination;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.Utils;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.discovery.DiscoverIslCommandData;
import org.openkilda.messaging.command.discovery.DiscoverPathCommandData;
import org.openkilda.messaging.command.discovery.NetworkCommandData;
import org.openkilda.messaging.command.flow.BaseInstallFlow;
import org.openkilda.messaging.command.flow.BatchInstallRequest;
import org.openkilda.messaging.command.flow.DeleteMeterRequest;
import org.openkilda.messaging.command.flow.MeterModifyCommandRequest;
import org.openkilda.messaging.command.flow.RemoveFlow;
import org.openkilda.messaging.command.switches.DumpMetersRequest;
import org.openkilda.messaging.command.switches.DumpPortDescriptionRequest;
import org.openkilda.messaging.command.switches.DumpRulesRequest;
import org.openkilda.messaging.command.switches.DumpSwitchPortsDescriptionRequest;
import org.openkilda.messaging.command.switches.PortConfigurationRequest;
import org.openkilda.messaging.command.switches.SwitchRulesDeleteRequest;
import org.openkilda.messaging.command.switches.SwitchRulesInstallRequest;
import org.openkilda.messaging.command.switches.ValidateRulesRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.floodlight.request.PingRequest;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.discovery.NetworkDumpSwitchData;
import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.floodlightrouter.ComponentType;
import org.openkilda.wfm.topology.floodlightrouter.Stream;
import org.openkilda.wfm.topology.floodlightrouter.service.FloodlightTracker;
import org.openkilda.wfm.topology.floodlightrouter.service.RequestTracker;
import org.openkilda.wfm.topology.utils.AbstractTickStatefulBolt;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.storm.state.InMemoryKeyValueState;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class RouterBolt extends AbstractTickStatefulBolt<InMemoryKeyValueState<String, Object>> {

    private static final Logger logger = LoggerFactory.getLogger(RouterBolt.class);
    private static final String FLOODLIGHT_TRACKER = "FLOODLIGHT_TRACKER";
    private static final String REQUEST_TRACKER = "REQUEST_TRACKER";


    private final Set<String> floodlights;
    private final long floodlightAliveTimeout;
    private final long floodlightRequestTimeout;
    private final long messageBlacklistTimeout;

    private transient RequestTracker requestTracker;
    private transient FloodlightTracker floodlightTracker;

    protected OutputCollector outputCollector;

    public RouterBolt(Set<String> floodlights, long floodlightAliveTimeout,
                      long floodlightRequestTimeout, long messageBlacklistTimeout) {
        this.floodlights = floodlights;
        this.floodlightAliveTimeout = floodlightAliveTimeout;
        this.floodlightRequestTimeout = floodlightRequestTimeout;
        this.messageBlacklistTimeout = messageBlacklistTimeout;
    }

    @Override
    protected void doTick(Tuple tuple) {
        CommandMessage message = null;
        try {

            for (String region : floodlights) {
                AliveRequest request = new AliveRequest();
                message = new CommandMessage(request, System.currentTimeMillis(), UUID.randomUUID()
                        .toString());
                String json = MAPPER.writeValueAsString(message);
                requestTracker.trackMessage(message.getCorrelationId());
                dispatchToSpeaker(tuple, json, region);
            }
        } catch (JsonProcessingException e) {
            logger.error("Unable to serialize {}", message);
        }
        requestTracker.cleanupOldMessages();
        floodlightTracker.checkTimeouts();
        floodlightTracker.handleUnmanagedSwitches(new RouterMessageSender(tuple,
                Stream.TOPO_DISCO));
    }

    @Override
    protected void doWork(Tuple input) {
        String sourceComponent = input.getSourceComponent();

        try {
            String json = input.getValueByField(AbstractTopology.MESSAGE_FIELD).toString();
            Message message = MAPPER.readValue(json, Message.class);
            switch (sourceComponent) {
                case ComponentType.ROUTER_TOPO_DISCO_SPOUT:
                    dispatchDiscoveryMessage(input, json, message);
                    break;
                case ComponentType.ROUTER_SPEAKER_KAFKA_SPOUT:
                    dispatchToSpeaker(input, json, message);
                    break;
                case ComponentType.SPEAKER_DISCO_KAFKA_SPOUT:
                    dispatchToDiscoSpeaker(input, json, message);
                    break;
                case ComponentType.ROUTER_SPEAKER_FLOW_KAFKA_SPOUT:
                    dispatchToSpeakerFlow(input, json, message);
                    break;
                case ComponentType.KILDA_FLOW_KAFKA_SPOUT:
                    dispatchToKildaFlow(input, json, message);
                    break;
                case ComponentType.SPEAKER_PING_KAFKA_SPOUT:
                    dispatchToSpeakerPing(input, json, message);
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            logger.error("failed to process message");
        }
        outputCollector.ack(input);
    }

    private void dispatchToSpeakerFlow(Tuple input, String json, Message message) {
        SwitchId switchId = lookupSwitchIdInCommandMessage(message);
        if (switchId != null) {
            requestTracker.trackMessage(message.getCorrelationId());
            String region = floodlightTracker.lookupRegion(switchId);
            String stream = formatWithRegion(Stream.SPEAKER_FLOW, region);
            Values values =  new Values(json);
            outputCollector.emit(stream, input, values);
        } else {
            logger.warn("Received message without target switch from SPEAKER_FLOW stream: {}", json);
        }
    }

    private void dispatchToSpeakerPing(Tuple input, String json, Message message) {
        SwitchId switchId = lookupSwitchIdInCommandMessage(message);
        if (switchId != null) {
            String region = floodlightTracker.lookupRegion(switchId);
            String stream = formatWithRegion(Stream.SPEAKER_PING, region);
            Values values =  new Values(json);
            outputCollector.emit(stream, input, values);
        } else {
            logger.warn("Received message without target switch from SPEAKER_PING stream: {}", json);
        }
    }


    private void dispatchDiscoveryMessage(Tuple input, String json, Message message) {
        if (message instanceof InfoMessage) {
            InfoMessage infoMessage = (InfoMessage) message;
            InfoData infoData = infoMessage.getData();
            SwitchId switchId;
            String region = ((InfoMessage) message).getRegion();
            if (infoData instanceof NetworkDumpSwitchData) {
                if (!requestTracker.checkReplyMessage(message.getCorrelationId())) {
                    logger.debug("Received outdated message {}", json);
                    return;
                }
                switchId = ((NetworkDumpSwitchData) infoData).getSwitchView().getDatapath();
                floodlightTracker.updateSwitchRegion(switchId, region);
            } else if (infoData instanceof SwitchInfoData) {
                switchId = ((SwitchInfoData) infoData).getSwitchId();
                floodlightTracker.updateSwitchRegion(switchId, region);
            } else if (infoData instanceof PortInfoData) {
                switchId = ((PortInfoData) infoData).getSwitchId();
                floodlightTracker.updateSwitchRegion(switchId, region);
            } else if (infoData instanceof AliveResponse) {
                if (!requestTracker.checkReplyMessage(message.getCorrelationId())) {
                    logger.debug("Received outdated message {}", json);
                    return;
                }
                handleAliveResponse(input, (AliveResponse) infoData, message.getTimestamp());
                return;
            }
        }
        Values values = new Values(json);
        outputCollector.emit(Stream.TOPO_DISCO, input, values);
    }

    private void dispatchToKildaFlow(Tuple input, String json, Message message) {
        if (!requestTracker.checkReplyMessage(message.getCorrelationId())) {
            return;
        }
        Values values = new Values(json);
        outputCollector.emit(Stream.KILDA_FLOW, input, values);
    }

    private SwitchId lookupSwitchIdInCommandMessage(Message message) {
        if (message instanceof CommandMessage) {
            CommandData commandData = ((CommandMessage) message).getData();
            if (commandData instanceof BaseInstallFlow) {
                return ((BaseInstallFlow) commandData).getSwitchId();
            } else if (commandData instanceof RemoveFlow) {
                return ((RemoveFlow) commandData).getSwitchId();
            } else if (commandData instanceof DiscoverIslCommandData) {
                return ((DiscoverIslCommandData) commandData).getSwitchId();
            } else if (commandData instanceof PingRequest) {
                return ((PingRequest) commandData).getPing().getSource().getDatapath();
            } else if (commandData instanceof DiscoverPathCommandData) {
                return ((DiscoverPathCommandData) commandData).getSrcSwitchId();
            } else if (commandData instanceof SwitchRulesDeleteRequest) {
                return ((SwitchRulesDeleteRequest) commandData).getSwitchId();
            } else if (commandData instanceof SwitchRulesInstallRequest) {
                return ((SwitchRulesInstallRequest) commandData).getSwitchId();
            } else if (commandData instanceof DumpRulesRequest) {
                return ((DumpRulesRequest) commandData).getSwitchId();
            } else if (commandData instanceof BatchInstallRequest) {
                return ((BatchInstallRequest) commandData).getSwitchId();
            } else if (commandData instanceof DeleteMeterRequest) {
                return ((DeleteMeterRequest) commandData).getSwitchId();
            } else if (commandData instanceof PortConfigurationRequest) {
                return ((PortConfigurationRequest) commandData).getSwitchId();
            } else if (commandData instanceof DumpSwitchPortsDescriptionRequest) {
                return ((DumpSwitchPortsDescriptionRequest) commandData).getSwitchId();
            } else if (commandData instanceof DumpPortDescriptionRequest) {
                return ((DumpPortDescriptionRequest) commandData).getSwitchId();
            } else if (commandData instanceof DumpMetersRequest) {
                return ((DumpMetersRequest) commandData).getSwitchId();
            } else if (commandData instanceof ValidateRulesRequest) {
                return ((ValidateRulesRequest) commandData).getSwitchId();
            } else if (commandData instanceof MeterModifyCommandRequest) {
                return ((MeterModifyCommandRequest) commandData).getFwdSwitchId();
            }
        }
        return null;
    }

    private void handleAliveResponse(Tuple input, AliveResponse response, long timestamp) {
        String region = response.getRegion();
        boolean requireSync = floodlightTracker.handleAliveResponse(region, timestamp);
        if (requireSync) {
            logger.info("Region {} requires sync", region);
            sendNetworkRequest(input, region);
        }
    }

    private void dispatchToSpeaker(Tuple input, String json, Message message) {
        SwitchId switchid = lookupSwitchIdInCommandMessage(message);
        requestTracker.trackMessage(message.getCorrelationId());
        if (switchid == null) {
            logger.debug("No target switch found, processing to all regions: {}", json);
            Values values = new Values(json);
            for (String region: floodlights) {
                String formattedStream = formatWithRegion(Stream.SPEAKER, region);
                outputCollector.emit(formattedStream, input, values);
            }
        } else {
            dispatchToSpeaker(input, json, message, switchid);
        }
    }

    private void dispatchToSpeaker(Tuple input, String json, Message message, SwitchId target) {
        String region = floodlightTracker.lookupRegion(target);
        if (region != null) {
            dispatchToSpeaker(input, json, region);
        } else {
            logger.error("Received command message for the untracked switch: {} {}", target, json);
            if (message instanceof CommandMessage) {
                CommandMessage commandMessage = (CommandMessage) message;
                if (commandMessage.getData() instanceof ValidateRulesRequest) {
                    String errorDetails = String.format("Switch %s was not found", target.toString());
                    ErrorData errorData = new ErrorData(ErrorType.NOT_FOUND, errorDetails, errorDetails);
                    ErrorMessage errorMessage = new ErrorMessage(errorData, System.currentTimeMillis(),
                            message.getCorrelationId(), null);
                    handleErroOnSwitchNotFound(input, errorMessage, Stream.NORTHBOUND_REPLY);
                }
            }
        }
    }

    private void dispatchToSpeaker(Tuple input, String json, String region) {
        String stream = formatWithRegion(Stream.SPEAKER, region);
        Values values =  new Values(json);
        outputCollector.emit(stream, input, values);
    }


    private void handleErroOnSwitchNotFound(Tuple tuple, Message message, String stream) {
        try {
            String json = Utils.MAPPER.writeValueAsString(message);
            outputCollector.emit(stream, tuple, new Values(json));
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize message {}", message);
        }
    }

    private String formatWithRegion(String stream, String region) {
        return String.format("%s_%s", stream, region);
    }

    private void dispatchToDiscoSpeaker(Tuple input, String json, Message message) {
        requestTracker.trackMessage(message.getCorrelationId());
        if (message instanceof CommandMessage) {
            CommandMessage commandMessage = (CommandMessage) message;
            CommandData commandData = commandMessage.getData();
            if (commandData instanceof DiscoverIslCommandData) {
                DiscoverIslCommandData data = (DiscoverIslCommandData) commandData;
                String region = floodlightTracker.lookupRegion(data.getSwitchId());
                String stream = formatWithRegion(Stream.SPEAKER_DISCO, region);
                Values values = new Values(json);
                outputCollector.emit(stream, input, values);
            }
        }
    }

    @Override
    public void initState(InMemoryKeyValueState<String, Object> state) {
        requestTracker = (RequestTracker) state.get(REQUEST_TRACKER);
        if (requestTracker == null) {
            requestTracker = new RequestTracker(floodlightRequestTimeout, messageBlacklistTimeout);
            state.put(REQUEST_TRACKER, requestTracker);
        }

        floodlightTracker = (FloodlightTracker) state.get(FLOODLIGHT_TRACKER);
        if (floodlightTracker == null) {
            floodlightTracker = new FloodlightTracker(floodlights, floodlightAliveTimeout);
            state.put(FLOODLIGHT_TRACKER, floodlightTracker);
        }
    }

    private String sendNetworkRequest(Tuple tuple, String region) {
        String correlationId = UUID.randomUUID().toString();
        requestTracker.trackMessage(correlationId);
        CommandMessage command = new CommandMessage(new NetworkCommandData(),
                System.currentTimeMillis(), correlationId,
                Destination.CONTROLLER);

        logger.info(
                "Send network dump request (correlation-id: {})",
                correlationId);

        try {
            String json = Utils.MAPPER.writeValueAsString(command);
            outputCollector.emit(formatWithRegion(Stream.SPEAKER, region), tuple, new Values(json));
        } catch (JsonProcessingException exception) {
            logger.error("Could not serialize network dump request", exception);
        }

        return correlationId;
    }



    @Override
    public void prepare(Map map, TopologyContext topologyContext, OutputCollector outputCollector) {
        this.outputCollector = outputCollector;
        super.prepare(map, topologyContext, outputCollector);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        for (String region : floodlights) {
            outputFieldsDeclarer.declareStream(formatWithRegion(Stream.SPEAKER, region),
                    new Fields(AbstractTopology.MESSAGE_FIELD));
            outputFieldsDeclarer.declareStream(formatWithRegion(Stream.SPEAKER_DISCO, region),
                    new Fields(AbstractTopology.MESSAGE_FIELD));
            outputFieldsDeclarer.declareStream(formatWithRegion(Stream.SPEAKER_FLOW, region),
                    new Fields(AbstractTopology.MESSAGE_FIELD));
            outputFieldsDeclarer.declareStream(formatWithRegion(Stream.SPEAKER_PING, region),
                    new Fields(AbstractTopology.MESSAGE_FIELD));
        }
        outputFieldsDeclarer.declareStream(Stream.TOPO_DISCO, new Fields(AbstractTopology.MESSAGE_FIELD));
        outputFieldsDeclarer.declareStream(Stream.KILDA_FLOW, new Fields(AbstractTopology.MESSAGE_FIELD));
        outputFieldsDeclarer.declareStream(Stream.NORTHBOUND_REPLY, new Fields(AbstractTopology.MESSAGE_FIELD));
    }


    public class RouterMessageSender {
        private Tuple tuple;
        private String stream;

        RouterMessageSender(Tuple tuple, String stream) {
            this.tuple = tuple;
            this.stream = stream;
        }

        /**
        * Send message object via target stream.
        * @param message object to be sent
        */
        public void send(Message message) {
            try {
                String json = MAPPER.writeValueAsString(message);
                Values values = new Values(json);
                outputCollector.emit(stream, tuple, values);
            } catch (JsonProcessingException e) {
                logger.error("failed to serialize message {}", message);
            }
        }

        /**
        * Send message object via target stream.
        * @param message serialized object to be sent
        */
        public void send(String message) {
            Values values = new Values(message);
            outputCollector.emit(stream, tuple, values);
        }
    }
}
