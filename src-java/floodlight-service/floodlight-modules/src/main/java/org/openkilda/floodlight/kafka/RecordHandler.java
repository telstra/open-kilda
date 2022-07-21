/* Copyright 2021 Telstra Open Source
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

package org.openkilda.floodlight.kafka;

import static java.lang.String.format;
import static org.openkilda.floodlight.kafka.ErrorMessageBuilder.anError;
import static org.openkilda.messaging.Utils.MAPPER;

import org.openkilda.floodlight.api.request.rulemanager.BaseSpeakerCommandsRequest;
import org.openkilda.floodlight.api.response.SpeakerDataResponse;
import org.openkilda.floodlight.command.Command;
import org.openkilda.floodlight.command.CommandContext;
import org.openkilda.floodlight.command.SpeakerCommand;
import org.openkilda.floodlight.command.SpeakerCommandReport;
import org.openkilda.floodlight.converter.OfFlowStatsMapper;
import org.openkilda.floodlight.converter.OfMeterConverter;
import org.openkilda.floodlight.converter.OfPortDescConverter;
import org.openkilda.floodlight.converter.rulemanager.OfFlowConverter;
import org.openkilda.floodlight.converter.rulemanager.OfGroupConverter;
import org.openkilda.floodlight.error.SwitchNotFoundException;
import org.openkilda.floodlight.error.SwitchOperationException;
import org.openkilda.floodlight.error.UnsupportedSwitchOperationException;
import org.openkilda.floodlight.kafka.dispatcher.BroadcastStatsRequestDispatcher;
import org.openkilda.floodlight.kafka.dispatcher.CommandDispatcher;
import org.openkilda.floodlight.kafka.dispatcher.PingRequestDispatcher;
import org.openkilda.floodlight.kafka.dispatcher.RemoveBfdSessionDispatcher;
import org.openkilda.floodlight.kafka.dispatcher.SetupBfdSessionDispatcher;
import org.openkilda.floodlight.service.CommandProcessorService;
import org.openkilda.floodlight.service.FeatureDetectorService;
import org.openkilda.floodlight.service.kafka.IKafkaProducerService;
import org.openkilda.floodlight.switchmanager.ISwitchManager;
import org.openkilda.floodlight.switchmanager.SwitchTrackingService;
import org.openkilda.floodlight.utils.CorrelationContext;
import org.openkilda.floodlight.utils.CorrelationContext.CorrelationContextClosable;
import org.openkilda.messaging.AliveRequest;
import org.openkilda.messaging.AliveResponse;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.MessageContext;
import org.openkilda.messaging.MessageData;
import org.openkilda.messaging.command.BroadcastWrapper;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.discovery.DiscoverIslCommandData;
import org.openkilda.messaging.command.discovery.DiscoverPathCommandData;
import org.openkilda.messaging.command.discovery.NetworkCommandData;
import org.openkilda.messaging.command.discovery.PortsCommandData;
import org.openkilda.messaging.command.flow.DeleteMeterRequest;
import org.openkilda.messaging.command.flow.MeterModifyCommandRequest;
import org.openkilda.messaging.command.switches.DeleteRulesCriteria;
import org.openkilda.messaging.command.switches.DumpGroupsForFlowHsRequest;
import org.openkilda.messaging.command.switches.DumpGroupsForSwitchManagerRequest;
import org.openkilda.messaging.command.switches.DumpMetersForFlowHsRequest;
import org.openkilda.messaging.command.switches.DumpMetersForSwitchManagerRequest;
import org.openkilda.messaging.command.switches.DumpMetersRequest;
import org.openkilda.messaging.command.switches.DumpPortDescriptionRequest;
import org.openkilda.messaging.command.switches.DumpRulesForFlowHsRequest;
import org.openkilda.messaging.command.switches.DumpRulesForSwitchManagerRequest;
import org.openkilda.messaging.command.switches.DumpRulesRequest;
import org.openkilda.messaging.command.switches.DumpSwitchPortsDescriptionRequest;
import org.openkilda.messaging.command.switches.PortConfigurationRequest;
import org.openkilda.messaging.command.switches.SwitchRulesDeleteRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.flow.FlowDumpResponse;
import org.openkilda.messaging.info.flow.SingleFlowDumpResponse;
import org.openkilda.messaging.info.group.GroupDumpResponse;
import org.openkilda.messaging.info.meter.MeterDumpResponse;
import org.openkilda.messaging.info.meter.MeterEntry;
import org.openkilda.messaging.info.meter.SwitchMeterEntries;
import org.openkilda.messaging.info.meter.SwitchMeterUnsupported;
import org.openkilda.messaging.info.rule.FlowEntry;
import org.openkilda.messaging.info.rule.GroupEntry;
import org.openkilda.messaging.info.rule.SwitchFlowEntries;
import org.openkilda.messaging.info.rule.SwitchGroupEntries;
import org.openkilda.messaging.info.stats.PortStatusData;
import org.openkilda.messaging.info.stats.SwitchPortStatusData;
import org.openkilda.messaging.info.switches.DeleteMeterResponse;
import org.openkilda.messaging.info.switches.PortConfigurationResponse;
import org.openkilda.messaging.info.switches.PortDescription;
import org.openkilda.messaging.info.switches.SwitchPortsDescription;
import org.openkilda.messaging.info.switches.SwitchRulesResponse;
import org.openkilda.model.PortStatus;
import org.openkilda.model.SwitchFeature;
import org.openkilda.model.SwitchId;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.GroupSpeakerData;
import org.openkilda.rulemanager.MeterSpeakerData;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import lombok.Getter;
import net.floodlightcontroller.core.IOFSwitch;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.projectfloodlight.openflow.protocol.OFFlowStatsEntry;
import org.projectfloodlight.openflow.protocol.OFGroupDescStatsEntry;
import org.projectfloodlight.openflow.protocol.OFMeterConfig;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.types.DatapathId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

class RecordHandler implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(RecordHandler.class);

    private final ConsumerContext context;
    private final List<CommandDispatcher<?>> dispatchers;
    private final ConsumerRecord<String, String> record;

    private final CommandProcessorService commandProcessor;
    private final FeatureDetectorService featureDetectorService;

    public RecordHandler(ConsumerContext context, List<CommandDispatcher<?>> dispatchers,
                         ConsumerRecord<String, String> record) {
        this.context = context;
        this.dispatchers = dispatchers;
        this.record = record;

        this.commandProcessor = context.getModuleContext().getServiceImpl(CommandProcessorService.class);
        this.featureDetectorService = context.getModuleContext().getServiceImpl(FeatureDetectorService.class);
    }

    @VisibleForTesting
    void handleCommand(CommandMessage message) {
        logger.debug("Handling message: '{}'.", message);
        CommandData data = message.getData();

        if (data instanceof DiscoverIslCommandData) {
            doDiscoverIslCommand((DiscoverIslCommandData) data, message.getCorrelationId());
        } else if (data instanceof DiscoverPathCommandData) {
            doDiscoverPathCommand(data);
        } else if (data instanceof NetworkCommandData) {
            doNetworkDump((NetworkCommandData) data);
        } else if (data instanceof SwitchRulesDeleteRequest) {
            doDeleteSwitchRules(message);
        } else if (data instanceof DumpRulesForFlowHsRequest) {
            doDumpRulesForFlowHsRequest(message);
        } else if (data instanceof DumpRulesRequest) {
            doDumpRulesRequest(message);
        } else if (data instanceof DumpRulesForSwitchManagerRequest) {
            doDumpRulesForSwitchManagerRequest(message);
        } else if (data instanceof DeleteMeterRequest) {
            doDeleteMeter(message, context.getKafkaNorthboundTopic());
        } else if (data instanceof PortConfigurationRequest) {
            doConfigurePort(message);
        } else if (data instanceof DumpSwitchPortsDescriptionRequest) {
            doDumpSwitchPortsDescriptionRequest(message);
        } else if (data instanceof DumpPortDescriptionRequest) {
            doDumpPortDescriptionRequest(message);
        } else if (data instanceof DumpMetersRequest) {
            doDumpMetersRequest(message);
        } else if (data instanceof DumpMetersForSwitchManagerRequest) {
            doDumpMetersForSwitchManagerRequest(message);
        } else if (data instanceof DumpMetersForFlowHsRequest) {
            doDumpMetersForFlowHsRequest(message);
        } else if (data instanceof MeterModifyCommandRequest) {
            doModifyMeterRequest(message);
        } else if (data instanceof AliveRequest) {
            doAliveRequest(message);
        } else if (data instanceof DumpGroupsForSwitchManagerRequest) {
            doDumpGroupsForSwitchManagerRequest(message);
        } else if (data instanceof DumpGroupsForFlowHsRequest) {
            doDumpGroupsForFlowHsRequest(message);
        } else if (data instanceof BroadcastWrapper) {
            handleBroadcastCommand(message, (BroadcastWrapper) data);
        } else {
            handlerNotFound(data);
        }
    }

    private void handleBroadcastCommand(CommandMessage message, BroadcastWrapper wrapper) {
        CommandData payload = wrapper.getPayload();
        if (payload instanceof PortsCommandData) {
            doPortsCommandDataRequest(wrapper.getScope(), (PortsCommandData) payload, message.getCorrelationId());
        } else {
            handlerNotFound(payload);
        }
    }

    private void doAliveRequest(CommandMessage message) {
        // TODO(tdurakov): return logic for failed amount counter
        int totalFailedAmount = getKafkaProducer().getFailedSendMessageCounter();
        getKafkaProducer().sendMessageAndTrack(context.getKafkaTopoDiscoTopic(),
                new InfoMessage(new AliveResponse(context.getRegion(), totalFailedAmount), System.currentTimeMillis(),
                        message.getCorrelationId(), context.getRegion()));
    }

    private void doDiscoverIslCommand(DiscoverIslCommandData command, String correlationId) {
        context.getDiscoveryEmitter().handleRequest(command, correlationId);
    }

    private void doDiscoverPathCommand(CommandData data) {
        DiscoverPathCommandData command = (DiscoverPathCommandData) data;
        logger.warn("NOT IMPLEMENTED: sending discover Path to {}", command);
    }

    /**
     * Create network dump for OFELinkBolt.
     */
    private void doNetworkDump(NetworkCommandData payload) {
        logger.info("Processing request from WFM to dump switches (dumpId: {})", payload.getDumpId());

        SwitchTrackingService switchTracking = context.getModuleContext().getServiceImpl(SwitchTrackingService.class);
        switchTracking.dumpAllSwitches(payload.getDumpId());
    }

    private void doDeleteSwitchRules(final CommandMessage message) {
        SwitchRulesDeleteRequest request = (SwitchRulesDeleteRequest) message.getData();
        logger.info("Deleting rules from '{}' switch: action={}, criteria={}", request.getSwitchId(),
                request.getDeleteRulesAction(), request.getCriteria());

        final IKafkaProducerService producerService = getKafkaProducer();
        final String replyToTopic = context.getKafkaSwitchManagerTopic();

        DatapathId dpid = DatapathId.of(request.getSwitchId().toLong());
        DeleteRulesCriteria criteria = request.getCriteria();

        ISwitchManager switchManager = context.getSwitchManager();

        try {
            List<Long> removedRules = new ArrayList<>();

            // The case when we delete by criteria.
            if (criteria != null) {
                removedRules.addAll(switchManager.deleteRulesByCriteria(dpid, criteria));
            }

            SwitchRulesResponse response = new SwitchRulesResponse(removedRules);
            InfoMessage infoMessage = new InfoMessage(response,
                    System.currentTimeMillis(), message.getCorrelationId());
            producerService.sendMessageAndTrack(replyToTopic, record.key(), infoMessage);

        } catch (SwitchNotFoundException e) {
            logger.error("Deleting switch rules was unsuccessful. Switch '{}' not found", request.getSwitchId());
            anError(ErrorType.NOT_FOUND)
                    .withMessage(e.getMessage())
                    .withDescription(request.getSwitchId().toString())
                    .withCorrelationId(message.getCorrelationId())
                    .withTopic(replyToTopic)
                    .withKey(record.key())
                    .sendVia(producerService);
        } catch (SwitchOperationException e) {
            logger.error("Failed to delete switch '{}' rules.", request.getSwitchId(), e);
            anError(ErrorType.DELETION_FAILURE)
                    .withMessage(e.getMessage())
                    .withDescription(request.getSwitchId().toString())
                    .withCorrelationId(message.getCorrelationId())
                    .withTopic(replyToTopic)
                    .withKey(record.key())
                    .sendVia(producerService);
        }
    }

    private void doDumpGroupsForSwitchManagerRequest(CommandMessage message) {
        SwitchId switchId = ((DumpGroupsForSwitchManagerRequest) message.getData()).getSwitchId();
        dumpRuleMangerGroupsRequest(switchId, buildSenderToSwitchManager(message));
    }

    private void doDumpGroupsForFlowHsRequest(CommandMessage message) {
        SwitchId switchId = ((DumpGroupsForFlowHsRequest) message.getData()).getSwitchId();
        dumpGroupsRequest(switchId, buildSenderToFlowHs(message));
    }

    private void dumpGroupsRequest(SwitchId switchId, java.util.function.Consumer<MessageData> sender) {
        try {
            logger.debug("Loading installed groups for switch {}", switchId);

            List<OFGroupDescStatsEntry> ofGroupDescStatsEntries = context.getSwitchManager()
                    .dumpGroups(DatapathId.of(switchId.toLong()));

            List<GroupEntry> groups = ofGroupDescStatsEntries.stream()
                    .map(OfFlowStatsMapper.INSTANCE::toFlowGroupEntry)
                    .collect(Collectors.toList());

            SwitchGroupEntries response = SwitchGroupEntries.builder()
                    .switchId(switchId)
                    .groupEntries(groups)
                    .build();
            sender.accept(response);
        } catch (SwitchOperationException e) {
            logger.error("Dumping of groups on switch '{}' was unsuccessful: {}", switchId, e.getMessage());
            ErrorData errorData = anError(ErrorType.NOT_FOUND)
                    .withMessage(e.getMessage())
                    .withDescription("The switch was not found when requesting a groups dump.")
                    .buildData();
            sender.accept(errorData);
        }
    }

    private void dumpRuleMangerGroupsRequest(SwitchId switchId, java.util.function.Consumer<MessageData> sender) {
        try {
            logger.debug("Loading installed groups for switch {}", switchId);

            List<OFGroupDescStatsEntry> ofGroupDescStatsEntries = context.getSwitchManager()
                    .dumpGroups(DatapathId.of(switchId.toLong()));

            List<GroupSpeakerData> groups = ofGroupDescStatsEntries.stream()
                    .map(group -> OfGroupConverter.INSTANCE.convertToGroupSpeakerData(group, switchId))
                    .collect(Collectors.toList());

            GroupDumpResponse response = GroupDumpResponse.builder()
                    .switchId(switchId)
                    .groupSpeakerData(groups)
                    .build();
            sender.accept(response);
        } catch (SwitchOperationException e) {
            logger.error("Dumping of groups on switch '{}' was unsuccessful: {}", switchId, e.getMessage());
            ErrorData errorData = anError(ErrorType.NOT_FOUND)
                    .withMessage(e.getMessage())
                    .withDescription("The switch was not found when requesting a groups dump.")
                    .buildData();
            sender.accept(errorData);
        }
    }

    private void doDumpRulesRequest(CommandMessage message) {
        processDumpRulesRequest(((DumpRulesRequest) message.getData()).getSwitchId(), buildSenderToNorthbound(message));
    }

    private void doDumpRulesForSwitchManagerRequest(CommandMessage message) {
        processDumpRuleManagerRulesRequest(((DumpRulesForSwitchManagerRequest) message.getData()).getSwitchId(),
                buildRulesSenderToSwitchManager(message));
    }

    private void doDumpRulesForFlowHsRequest(CommandMessage message) {
        processDumpRulesRequest(((DumpRulesForFlowHsRequest) message.getData()).getSwitchId(),
                buildSenderToFlowHs(message));
    }

    private void processDumpRulesRequest(SwitchId switchId, java.util.function.Consumer<MessageData> sender) {
        try {
            logger.debug("Loading installed rules for switch {}", switchId);

            List<OFFlowStatsEntry> flowEntries =
                    context.getSwitchManager().dumpFlowTable(DatapathId.of(switchId.toLong()));
            List<FlowEntry> flows = flowEntries.stream()
                    .map(OfFlowStatsMapper.INSTANCE::toFlowEntry)
                    .collect(Collectors.toList());

            SwitchFlowEntries response = SwitchFlowEntries.builder()
                    .switchId(switchId)
                    .flowEntries(flows)
                    .build();
            sender.accept(response);
        } catch (SwitchOperationException e) {
            logger.error("Dumping of rules on switch '{}' was unsuccessful: {}", switchId, e.getMessage());
            ErrorData errorData = anError(ErrorType.NOT_FOUND)
                    .withMessage(e.getMessage())
                    .withDescription("The switch was not found when requesting a rules dump.")
                    .buildData();
            sender.accept(errorData);
        }
    }

    private void processDumpRuleManagerRulesRequest(SwitchId switchId,
                                                    java.util.function.Consumer<MessageData> sender) {
        try {
            logger.debug("Loading installed rules for switch {}", switchId);

            List<OFFlowStatsEntry> flowEntries =
                    context.getSwitchManager().dumpFlowTable(DatapathId.of(switchId.toLong()));
            List<FlowSpeakerData> flows = flowEntries.stream()
                    .map(entry -> OfFlowConverter.INSTANCE.convertToFlowSpeakerData(entry, switchId))
                    .collect(Collectors.toList());

            FlowDumpResponse response = FlowDumpResponse.builder()
                    .flowSpeakerData(flows)
                    .build();
            sender.accept(response);
        } catch (SwitchNotFoundException e) {
            logger.error("Dumping of rules on switch '{}' was unsuccessful: {}", switchId, e.getMessage());
            ErrorData errorData = anError(ErrorType.NOT_FOUND)
                    .withMessage(e.getMessage())
                    .withDescription("The switch was not found when requesting a rules dump.")
                    .buildData();
            sender.accept(errorData);
        }
    }

    private void doPortsCommandDataRequest(Set<SwitchId> scope, PortsCommandData payload, String correlationId) {
        ISwitchManager switchManager = context.getModuleContext().getServiceImpl(ISwitchManager.class);

        try {
            logger.info("Getting ports data. Requester: {}", payload.getRequester());
            Map<DatapathId, IOFSwitch> allSwitchMap = context.getSwitchManager().getAllSwitchMap(true);
            for (Map.Entry<DatapathId, IOFSwitch> entry : allSwitchMap.entrySet()) {
                SwitchId switchId = new SwitchId(entry.getKey().toString());
                if (! scope.contains(switchId)) {
                    continue;
                }

                try {
                    IOFSwitch sw = entry.getValue();

                    Set<PortStatusData> statuses = new HashSet<>();
                    for (OFPortDesc portDesc : switchManager.getPhysicalPorts(sw.getId())) {
                        statuses.add(new PortStatusData(portDesc.getPortNo().getPortNumber(),
                                portDesc.isEnabled() ? PortStatus.UP : PortStatus.DOWN));
                    }

                    SwitchPortStatusData response = SwitchPortStatusData.builder()
                            .switchId(switchId)
                            .ports(statuses)
                            .requester(payload.getRequester())
                            .build();

                    InfoMessage infoMessage = new InfoMessage(
                            response, System.currentTimeMillis(), correlationId);
                    getKafkaProducer().sendMessageAndTrack(context.getKafkaStatsTopic(), infoMessage);
                } catch (Exception e) {
                    logger.error("Could not get port stats data for switch '{}' with error '{}'",
                            switchId, e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            logger.error("Could not get port data for stats '{}'", e.getMessage(), e);
        }
    }

    private void doDeleteMeter(CommandMessage message, String replyToTopic) {
        DeleteMeterRequest request = (DeleteMeterRequest) message.getData();
        logger.info("Deleting meter '{}'. Switch: '{}'", request.getMeterId(), request.getSwitchId());

        final IKafkaProducerService producerService = getKafkaProducer();

        try {
            DatapathId dpid = DatapathId.of(request.getSwitchId().toLong());
            context.getSwitchManager().deleteMeter(dpid, request.getMeterId());

            boolean deleted = context.getSwitchManager().dumpMeters(dpid)
                    .stream()
                    .noneMatch(config -> config.getMeterId() == request.getMeterId());
            DeleteMeterResponse response = new DeleteMeterResponse(deleted);
            InfoMessage infoMessage = new InfoMessage(response, System.currentTimeMillis(), message.getCorrelationId());
            producerService.sendMessageAndTrack(replyToTopic, message.getCorrelationId(), infoMessage);
        } catch (SwitchOperationException e) {
            logger.error("Deleting meter '{}' from switch '{}' was unsuccessful: {}",
                    request.getMeterId(), request.getSwitchId(), e.getMessage());
            anError(ErrorType.DATA_INVALID)
                    .withMessage(e.getMessage())
                    .withDescription(request.getSwitchId().toString())
                    .withCorrelationId(message.getCorrelationId())
                    .withTopic(replyToTopic)
                    .sendVia(producerService);
        }
    }

    private void doConfigurePort(final CommandMessage message) {
        PortConfigurationRequest request = (PortConfigurationRequest) message.getData();

        logger.info("Port configuration request. Switch '{}', Port '{}'", request.getSwitchId(),
                request.getPortNumber());

        final IKafkaProducerService producerService = getKafkaProducer();
        final String replyToTopic = context.getKafkaNorthboundTopic();

        try {
            ISwitchManager switchManager = context.getSwitchManager();

            DatapathId dpId = DatapathId.of(request.getSwitchId().toLong());
            switchManager.configurePort(dpId, request.getPortNumber(), request.getAdminDown());

            InfoMessage infoMessage = new InfoMessage(
                    new PortConfigurationResponse(request.getSwitchId(), request.getPortNumber()),
                    message.getTimestamp(),
                    message.getCorrelationId());
            producerService.sendMessageAndTrack(replyToTopic, infoMessage);
        } catch (SwitchOperationException e) {
            logger.error("Port configuration request failed. " + e.getMessage(), e);
            anError(ErrorType.DATA_INVALID)
                    .withMessage(e.getMessage())
                    .withDescription("Port configuration request failed")
                    .withCorrelationId(message.getCorrelationId())
                    .withTopic(replyToTopic)
                    .sendVia(producerService);
        }
    }

    private void doDumpSwitchPortsDescriptionRequest(CommandMessage message) {
        DumpSwitchPortsDescriptionRequest request = (DumpSwitchPortsDescriptionRequest) message.getData();

        final IKafkaProducerService producerService = getKafkaProducer();
        final String replyToTopic = context.getKafkaNorthboundTopic();

        try {
            SwitchId switchId = request.getSwitchId();
            logger.info("Dump ALL ports description for switch {}", switchId);

            SwitchPortsDescription response = getSwitchPortsDescription(switchId);

            InfoMessage infoMessage = new InfoMessage(response, message.getTimestamp(), message.getCorrelationId());
            producerService.sendMessageAndTrack(replyToTopic, infoMessage);
        } catch (SwitchOperationException e) {
            logger.error("Unable to dump switch port descriptions request", e);
            anError(ErrorType.NOT_FOUND)
                    .withMessage(e.getMessage())
                    .withDescription("Unable to dump switch port descriptions request")
                    .withCorrelationId(message.getCorrelationId())
                    .withTopic(replyToTopic)
                    .sendVia(producerService);
        }
    }

    private SwitchPortsDescription getSwitchPortsDescription(SwitchId switchId) throws SwitchOperationException {

        List<OFPortDesc> ofPortsDescriptions =
                context.getSwitchManager().dumpPortsDescription(DatapathId.of(switchId.toLong()));
        List<PortDescription> portsDescriptions = ofPortsDescriptions.stream()
                .map(OfPortDescConverter.INSTANCE::toPortDescription)
                .collect(Collectors.toList());

        return SwitchPortsDescription.builder()
                .version(ofPortsDescriptions.get(0).getVersion().toString())
                .portsDescription(portsDescriptions)
                .build();
    }

    private void doDumpPortDescriptionRequest(CommandMessage message) {
        DumpPortDescriptionRequest request = (DumpPortDescriptionRequest) message.getData();

        final IKafkaProducerService producerService = getKafkaProducer();
        final String replyToTopic = context.getKafkaNorthboundTopic();

        try {
            SwitchId switchId = request.getSwitchId();
            logger.info("Get port {}_{} description", switchId, request.getPortNumber());
            SwitchPortsDescription switchPortsDescription = getSwitchPortsDescription(switchId);

            int port = request.getPortNumber();
            PortDescription response = switchPortsDescription.getPortsDescription()
                    .stream()
                    .filter(x -> x.getPortNumber() == port)
                    .findFirst()
                    .orElseThrow(() -> new SwitchOperationException(
                            DatapathId.of(switchId.toLong()),
                            format("Port %s_%d does not exists.", switchId, port)));

            InfoMessage infoMessage = new InfoMessage(response, message.getTimestamp(), message.getCorrelationId());
            producerService.sendMessageAndTrack(replyToTopic, infoMessage);
        } catch (SwitchOperationException e) {
            logger.error("Unable to dump port description request", e);
            anError(ErrorType.NOT_FOUND)
                    .withMessage(e.getMessage())
                    .withDescription("Unable to dump port description request")
                    .withCorrelationId(message.getCorrelationId())
                    .withTopic(replyToTopic)
                    .sendVia(producerService);
        }
    }

    private void doDumpMetersRequest(CommandMessage message) {
        DumpMetersRequest request = (DumpMetersRequest) message.getData();
        dumpMeters(request.getSwitchId(), buildSenderToNorthbound(message));
    }

    private void doDumpMetersForSwitchManagerRequest(CommandMessage message) {
        DumpMetersForSwitchManagerRequest request = (DumpMetersForSwitchManagerRequest) message.getData();
        dumpRuleManagerMeters(request.getSwitchId(), buildSenderToSwitchManager(message));
    }

    private void doDumpMetersForFlowHsRequest(CommandMessage message) {
        DumpMetersForFlowHsRequest request = (DumpMetersForFlowHsRequest) message.getData();
        dumpMeters(request.getSwitchId(), buildSenderToFlowHs(message));
    }

    private java.util.function.Consumer<MessageData> buildSenderToSwitchManager(Message message) {
        return buildSenderToTopic(context.getKafkaSwitchManagerTopic(),
                message.getCorrelationId(), message.getTimestamp());
    }

    private java.util.function.Consumer<MessageData> buildSenderToNorthbound(Message message) {
        return buildSenderToTopic(context.getKafkaNorthboundTopic(),
                message.getCorrelationId(), message.getTimestamp());
    }

    private java.util.function.Consumer<MessageData> buildSenderToTopic(String kafkaTopic,
                                                                        String correlationId, long timestamp) {
        IKafkaProducerService producerService = getKafkaProducer();
        return data -> {
            Message result;
            if (data instanceof InfoData) {
                result = new InfoMessage((InfoData) data, timestamp, correlationId);
            } else if (data instanceof ErrorData) {
                result = new ErrorMessage((ErrorData) data, timestamp, correlationId);
            } else {
                throw new IllegalArgumentException("Unsupported data: " + data);
            }
            producerService.sendMessageAndTrack(kafkaTopic, correlationId, result);
        };
    }

    private java.util.function.Consumer<MessageData> buildSenderToFlowHs(Message message) {
        IKafkaProducerService producerService = getKafkaProducer();
        return data -> {
            MessageContext messageContext = new MessageContext(message);
            SpeakerDataResponse result = new SpeakerDataResponse(messageContext, data);
            producerService.sendMessageAndTrack(context.getKafkaSpeakerFlowHsTopic(),
                    message.getCorrelationId(), result);
        };
    }

    private java.util.function.Consumer<MessageData> buildRulesSenderToSwitchManager(Message message) {
        IKafkaProducerService producerService = getKafkaProducer();
        return data -> {
            FlowDumpResponse entries = (FlowDumpResponse) data;
            List<SingleFlowDumpResponse> result = new ArrayList<>();
            for (FlowSpeakerData speakerData : entries.getFlowSpeakerData()) {
                result.add(new SingleFlowDumpResponse(speakerData));
            }
            producerService.sendChunkedMessageAndTrack(
                    context.getKafkaSwitchManagerTopic(), message.getCorrelationId(), result);
        };
    }

    private void dumpMeters(SwitchId switchId, java.util.function.Consumer<MessageData> sender) {
        try {
            logger.debug("Get all meters for switch {}", switchId);
            ISwitchManager switchManager = context.getSwitchManager();
            List<OFMeterConfig> meterEntries = switchManager.dumpMeters(DatapathId.of(switchId.toLong()));
            List<MeterEntry> meters = meterEntries.stream()
                    .map(OfMeterConverter::toMeterEntry)
                    .collect(Collectors.toList());

            SwitchMeterEntries response = SwitchMeterEntries.builder()
                    .switchId(switchId)
                    .meterEntries(meters)
                    .build();
            sender.accept(response);
        } catch (UnsupportedSwitchOperationException e) {
            logger.info("Meters not supported: {}", switchId);
            sender.accept(new SwitchMeterUnsupported(switchId));
        } catch (SwitchNotFoundException e) {
            logger.info("Dumping switch meters is unsuccessful. Switch {} not found", switchId);
            ErrorData errorData = anError(ErrorType.NOT_FOUND)
                    .withMessage(e.getMessage())
                    .withDescription(switchId.toString())
                    .buildData();
            sender.accept(errorData);
        } catch (SwitchOperationException e) {
            logger.error("Unable to dump meters", e);
            ErrorData errorData = anError(ErrorType.NOT_FOUND)
                    .withMessage(e.getMessage())
                    .withDescription("Unable to dump meters")
                    .buildData();
            sender.accept(errorData);
        }
    }

    private void dumpRuleManagerMeters(SwitchId switchId, java.util.function.Consumer<MessageData> sender) {
        try {
            logger.debug("Get all meters for switch {}", switchId);
            ISwitchManager switchManager = context.getSwitchManager();
            DatapathId datapathId = DatapathId.of(switchId.toLong());
            List<OFMeterConfig> meterEntries = switchManager.dumpMeters(datapathId);
            IOFSwitch iofSwitch = switchManager.lookupSwitch(datapathId);
            boolean inaccurate = featureDetectorService.detectSwitch(iofSwitch)
                    .contains(SwitchFeature.INACCURATE_METER);
            List<MeterSpeakerData> meters = meterEntries.stream()
                    .map(entry -> org.openkilda.floodlight.converter.rulemanager.OfMeterConverter.INSTANCE
                            .convertToMeterSpeakerData(entry, inaccurate))
                    .collect(Collectors.toList());

            MeterDumpResponse response = MeterDumpResponse.builder()
                    .switchId(switchId)
                    .meterSpeakerData(meters)
                    .build();
            sender.accept(response);
        } catch (UnsupportedSwitchOperationException e) {
            logger.info("Meters not supported: {}", switchId);
            sender.accept(new SwitchMeterUnsupported(switchId));
        } catch (SwitchNotFoundException e) {
            logger.info("Dumping switch meters is unsuccessful. Switch {} not found", switchId);
            ErrorData errorData = anError(ErrorType.NOT_FOUND)
                    .withMessage(e.getMessage())
                    .withDescription(switchId.toString())
                    .buildData();
            sender.accept(errorData);
        } catch (SwitchOperationException e) {
            logger.error("Unable to dump meters", e);
            ErrorData errorData = anError(ErrorType.NOT_FOUND)
                    .withMessage(e.getMessage())
                    .withDescription("Unable to dump meters")
                    .buildData();
            sender.accept(errorData);
        }
    }

    private void doModifyMeterRequest(CommandMessage message) {
        MeterModifyCommandRequest request = (MeterModifyCommandRequest) message.getData();

        final IKafkaProducerService producerService = getKafkaProducer();
        String replyToTopic = context.getKafkaNbWorkerTopic();

        SwitchId switchId = request.getSwitchId();

        DatapathId datapathId = DatapathId.of(switchId.toLong());
        long meterId = request.getMeterId();

        ISwitchManager switchManager = context.getSwitchManager();

        try {
            switchManager.modifyMeterForFlow(datapathId, meterId, request.getBandwidth());

            MeterEntry meterEntry = OfMeterConverter.toMeterEntry(switchManager.dumpMeterById(datapathId, meterId));

            SwitchMeterEntries response = SwitchMeterEntries.builder()
                    .switchId(switchId)
                    .meterEntries(ImmutableList.of(meterEntry))
                    .build();

            InfoMessage infoMessage = new InfoMessage(response, message.getTimestamp(), message.getCorrelationId());
            producerService.sendMessageAndTrack(replyToTopic, message.getCorrelationId(), infoMessage);
        } catch (UnsupportedSwitchOperationException e) {
            String messageString = String.format("Not supported: %s", new SwitchId(e.getDpId().getLong()));
            logger.error(messageString, e);
            anError(ErrorType.PARAMETERS_INVALID)
                    .withMessage(e.getMessage())
                    .withDescription(messageString)
                    .withCorrelationId(message.getCorrelationId())
                    .withTopic(replyToTopic)
                    .sendVia(producerService);
        } catch (SwitchNotFoundException e) {
            logger.error("Update switch meters is unsuccessful. Switch {} not found",
                    new SwitchId(e.getDpId().getLong()));
            anError(ErrorType.NOT_FOUND)
                    .withMessage(e.getMessage())
                    .withDescription(new SwitchId(e.getDpId().getLong()).toString())
                    .withCorrelationId(message.getCorrelationId())
                    .withTopic(replyToTopic)
                    .sendVia(producerService);
        } catch (SwitchOperationException e) {
            String messageString = "Unable to update meter";
            logger.error(messageString, e);
            anError(ErrorType.NOT_FOUND)
                    .withMessage(e.getMessage())
                    .withDescription(messageString)
                    .withCorrelationId(message.getCorrelationId())
                    .withTopic(replyToTopic)
                    .sendVia(producerService);
        }
    }

    private void parseRecord(ConsumerRecord<String, String> record) {
        if (handleSpeakerCommand()) {
            return;
        }
        if (handleRuleManagerCommand(record.topic())) {
            return;
        }

        CommandMessage message;
        try {
            String value = record.value();
            // TODO: Prior to Message changes, this MAPPER would read Message ..
            //          but, changed to BaseMessage and got an error wrt "timestamp" ..
            //          so, need to experiment with why CommandMessage can't be read as
            //          a BaseMessage
            message = MAPPER.readValue(value, CommandMessage.class);
        } catch (Exception exception) {
            logger.error("error parsing record '{}'", record.value(), exception);
            return;
        }

        // Process the message within the message correlation context.
        try (CorrelationContextClosable closable = CorrelationContext.create(message.getCorrelationId())) {
            if (logger.isDebugEnabled()) {
                logger.debug("Receive command: key={}, payload={}", record.key(), message.getData());
            }

            CommandContext commandContext = new CommandContext(context.getModuleContext(), message.getCorrelationId(),
                    record.key());
            if (!dispatch(commandContext, message)) {
                handleCommand(message);
            }
        } catch (Exception exception) {
            logger.error("error processing message '{}'", message, exception);
        }
    }

    private boolean handleSpeakerCommand() {
        SpeakerCommand<SpeakerCommandReport> speakerCommand = null;
        try {
            TypeReference<SpeakerCommand<SpeakerCommandReport>> commandType
                    = new TypeReference<SpeakerCommand<SpeakerCommandReport>>() {};
            speakerCommand = MAPPER.readValue(record.value(), commandType);
        } catch (JsonMappingException e) {
            logger.trace("Received deprecated command message");
            return false;
        } catch (IOException e) {
            logger.error("Error while parsing record {}", record.value(), e);
            return false;
        }

        handleSpeakerCommand(speakerCommand);
        return true;
    }

    private void handleSpeakerCommand(SpeakerCommand<? extends SpeakerCommandReport> command) {
        final MessageContext messageContext = command.getMessageContext();
        try (CorrelationContextClosable closable =
                     CorrelationContext.create(messageContext.getCorrelationId())) {
            context.getCommandProcessor().process(command, record.key());
        }
    }

    private boolean handleRuleManagerCommand(String sourceTopic) {
        BaseSpeakerCommandsRequest request;
        try {
            request = MAPPER.readValue(record.value(), BaseSpeakerCommandsRequest.class);
        } catch (JsonMappingException e) {
            logger.trace("Received deprecated command message");
            return false;
        } catch (IOException e) {
            logger.error("Error while parsing record {}", record.value(), e);
            return false;
        }

        request.setSourceTopic(sourceTopic);
        try {
            handleRuleManagerCommand(request);
        } catch (Exception e) {
            logger.error("Error while processing request {}", request, e);
        }
        return true;
    }

    private void handleRuleManagerCommand(BaseSpeakerCommandsRequest command) {
        final MessageContext messageContext = command.getMessageContext();
        try (CorrelationContextClosable closable =
                     CorrelationContext.create(messageContext.getCorrelationId())) {
            command.process(context.getOfSpeakerService(), record.key());
        }
    }

    @Override
    public void run() {
        parseRecord(record);
    }

    private boolean dispatch(CommandContext commandContext, CommandMessage message) {
        CommandData payload = message.getData();

        for (CommandDispatcher<?> entry : dispatchers) {
            Optional<Command> command = entry.dispatch(commandContext, payload);
            if (!command.isPresent()) {
                continue;
            }

            commandProcessor.process(command.get());
            return true;
        }

        return false;
    }

    private IKafkaProducerService getKafkaProducer() {
        return context.getModuleContext().getServiceImpl(IKafkaProducerService.class);
    }

    private void handlerNotFound(CommandData payload) {
        logger.error("Unable to handle '{}' request - handler not found.", payload);
    }

    public static class Factory {
        @Getter
        private final ConsumerContext context;
        private final List<CommandDispatcher<?>> dispatchers = ImmutableList.of(
                new PingRequestDispatcher(),
                new SetupBfdSessionDispatcher(),
                new RemoveBfdSessionDispatcher(),
                new BroadcastStatsRequestDispatcher());

        public Factory(ConsumerContext context) {
            this.context = context;
        }

        public RecordHandler produce(ConsumerRecord<String, String> record) {
            return new RecordHandler(context, dispatchers, record);
        }
    }
}
