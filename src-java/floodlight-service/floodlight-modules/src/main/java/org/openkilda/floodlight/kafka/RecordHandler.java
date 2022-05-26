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
import static org.openkilda.floodlight.switchmanager.SwitchManager.INGRESS_TABLE_ID;
import static org.openkilda.floodlight.switchmanager.SwitchManager.POST_INGRESS_TABLE_ID;
import static org.openkilda.floodlight.switchmanager.SwitchManager.TRANSIT_TABLE_ID;
import static org.openkilda.messaging.Utils.MAPPER;
import static org.openkilda.model.cookie.Cookie.ARP_INGRESS_COOKIE;
import static org.openkilda.model.cookie.Cookie.ARP_INPUT_PRE_DROP_COOKIE;
import static org.openkilda.model.cookie.Cookie.ARP_POST_INGRESS_COOKIE;
import static org.openkilda.model.cookie.Cookie.ARP_POST_INGRESS_ONE_SWITCH_COOKIE;
import static org.openkilda.model.cookie.Cookie.ARP_POST_INGRESS_VXLAN_COOKIE;
import static org.openkilda.model.cookie.Cookie.ARP_TRANSIT_COOKIE;
import static org.openkilda.model.cookie.Cookie.CATCH_BFD_RULE_COOKIE;
import static org.openkilda.model.cookie.Cookie.DROP_RULE_COOKIE;
import static org.openkilda.model.cookie.Cookie.DROP_VERIFICATION_LOOP_RULE_COOKIE;
import static org.openkilda.model.cookie.Cookie.LLDP_INGRESS_COOKIE;
import static org.openkilda.model.cookie.Cookie.LLDP_INPUT_PRE_DROP_COOKIE;
import static org.openkilda.model.cookie.Cookie.LLDP_POST_INGRESS_COOKIE;
import static org.openkilda.model.cookie.Cookie.LLDP_POST_INGRESS_ONE_SWITCH_COOKIE;
import static org.openkilda.model.cookie.Cookie.LLDP_POST_INGRESS_VXLAN_COOKIE;
import static org.openkilda.model.cookie.Cookie.LLDP_TRANSIT_COOKIE;
import static org.openkilda.model.cookie.Cookie.MULTITABLE_EGRESS_PASS_THROUGH_COOKIE;
import static org.openkilda.model.cookie.Cookie.MULTITABLE_INGRESS_DROP_COOKIE;
import static org.openkilda.model.cookie.Cookie.MULTITABLE_POST_INGRESS_DROP_COOKIE;
import static org.openkilda.model.cookie.Cookie.MULTITABLE_PRE_INGRESS_PASS_THROUGH_COOKIE;
import static org.openkilda.model.cookie.Cookie.MULTITABLE_TRANSIT_DROP_COOKIE;
import static org.openkilda.model.cookie.Cookie.ROUND_TRIP_LATENCY_RULE_COOKIE;
import static org.openkilda.model.cookie.Cookie.SERVER_42_FLOW_RTT_OUTPUT_VLAN_COOKIE;
import static org.openkilda.model.cookie.Cookie.SERVER_42_FLOW_RTT_OUTPUT_VXLAN_COOKIE;
import static org.openkilda.model.cookie.Cookie.SERVER_42_FLOW_RTT_TURNING_COOKIE;
import static org.openkilda.model.cookie.Cookie.SERVER_42_FLOW_RTT_VXLAN_TURNING_COOKIE;
import static org.openkilda.model.cookie.Cookie.SERVER_42_ISL_RTT_OUTPUT_COOKIE;
import static org.openkilda.model.cookie.Cookie.SERVER_42_ISL_RTT_TURNING_COOKIE;
import static org.openkilda.model.cookie.Cookie.VERIFICATION_BROADCAST_RULE_COOKIE;
import static org.openkilda.model.cookie.Cookie.VERIFICATION_UNICAST_RULE_COOKIE;
import static org.openkilda.model.cookie.Cookie.VERIFICATION_UNICAST_VXLAN_RULE_COOKIE;

import org.openkilda.floodlight.api.request.rulemanager.BaseSpeakerCommandsRequest;
import org.openkilda.floodlight.api.response.SpeakerDataResponse;
import org.openkilda.floodlight.command.Command;
import org.openkilda.floodlight.command.CommandContext;
import org.openkilda.floodlight.command.SpeakerCommand;
import org.openkilda.floodlight.command.SpeakerCommandReport;
import org.openkilda.floodlight.command.flow.FlowSegmentResponseFactory;
import org.openkilda.floodlight.command.flow.FlowSegmentSyncResponseFactory;
import org.openkilda.floodlight.command.flow.FlowSegmentWrapperCommand;
import org.openkilda.floodlight.command.flow.egress.EgressFlowSegmentInstallCommand;
import org.openkilda.floodlight.command.flow.egress.EgressMirrorFlowSegmentInstallCommand;
import org.openkilda.floodlight.command.flow.ingress.IngressFlowLoopSegmentInstallCommand;
import org.openkilda.floodlight.command.flow.ingress.IngressFlowSegmentInstallCommand;
import org.openkilda.floodlight.command.flow.ingress.IngressMirrorFlowSegmentInstallCommand;
import org.openkilda.floodlight.command.flow.ingress.IngressServer42FlowInstallCommand;
import org.openkilda.floodlight.command.flow.ingress.OneSwitchFlowInstallCommand;
import org.openkilda.floodlight.command.flow.ingress.OneSwitchMirrorFlowInstallCommand;
import org.openkilda.floodlight.command.flow.transit.TransitFlowLoopSegmentInstallCommand;
import org.openkilda.floodlight.command.flow.transit.TransitFlowSegmentInstallCommand;
import org.openkilda.floodlight.command.group.GroupInstallCommand;
import org.openkilda.floodlight.command.group.GroupModifyCommand;
import org.openkilda.floodlight.command.group.GroupRemoveCommand;
import org.openkilda.floodlight.command.meter.MeterModifyCommand;
import org.openkilda.floodlight.converter.OfFlowStatsMapper;
import org.openkilda.floodlight.converter.OfMeterConverter;
import org.openkilda.floodlight.converter.OfPortDescConverter;
import org.openkilda.floodlight.converter.rulemanager.OfFlowConverter;
import org.openkilda.floodlight.converter.rulemanager.OfGroupConverter;
import org.openkilda.floodlight.error.FlowCommandException;
import org.openkilda.floodlight.error.InvalidMeterIdException;
import org.openkilda.floodlight.error.OfInstallException;
import org.openkilda.floodlight.error.SwitchNotFoundException;
import org.openkilda.floodlight.error.SwitchOperationException;
import org.openkilda.floodlight.error.UnsupportedSwitchOperationException;
import org.openkilda.floodlight.kafka.dispatcher.BroadcastStatsRequestDispatcher;
import org.openkilda.floodlight.kafka.dispatcher.CommandDispatcher;
import org.openkilda.floodlight.kafka.dispatcher.PingRequestDispatcher;
import org.openkilda.floodlight.kafka.dispatcher.RemoveBfdSessionDispatcher;
import org.openkilda.floodlight.kafka.dispatcher.SetupBfdSessionDispatcher;
import org.openkilda.floodlight.model.FlowSegmentMetadata;
import org.openkilda.floodlight.model.FlowTransitData;
import org.openkilda.floodlight.model.RulesContext;
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
import org.openkilda.messaging.command.flow.BaseFlow;
import org.openkilda.messaging.command.flow.BaseInstallFlow;
import org.openkilda.messaging.command.flow.DeleteMeterRequest;
import org.openkilda.messaging.command.flow.InstallEgressFlow;
import org.openkilda.messaging.command.flow.InstallEgressMirrorFlow;
import org.openkilda.messaging.command.flow.InstallFlowForSwitchManagerRequest;
import org.openkilda.messaging.command.flow.InstallIngressFlow;
import org.openkilda.messaging.command.flow.InstallIngressLoopFlow;
import org.openkilda.messaging.command.flow.InstallIngressMirrorFlow;
import org.openkilda.messaging.command.flow.InstallOneSwitchFlow;
import org.openkilda.messaging.command.flow.InstallOneSwitchMirrorFlow;
import org.openkilda.messaging.command.flow.InstallServer42Flow;
import org.openkilda.messaging.command.flow.InstallServer42IngressFlow;
import org.openkilda.messaging.command.flow.InstallSharedFlow;
import org.openkilda.messaging.command.flow.InstallTransitFlow;
import org.openkilda.messaging.command.flow.InstallTransitLoopFlow;
import org.openkilda.messaging.command.flow.MeterModifyCommandRequest;
import org.openkilda.messaging.command.flow.ModifyDefaultMeterForSwitchManagerRequest;
import org.openkilda.messaging.command.flow.ModifyFlowMeterForSwitchManagerRequest;
import org.openkilda.messaging.command.flow.ReinstallDefaultFlowForSwitchManagerRequest;
import org.openkilda.messaging.command.flow.ReinstallServer42FlowForSwitchManagerRequest;
import org.openkilda.messaging.command.flow.RemoveFlow;
import org.openkilda.messaging.command.flow.RemoveFlowForSwitchManagerRequest;
import org.openkilda.messaging.command.switches.ConnectModeRequest;
import org.openkilda.messaging.command.switches.DeleteGroupRequest;
import org.openkilda.messaging.command.switches.DeleteRulesCriteria;
import org.openkilda.messaging.command.switches.DeleterMeterForSwitchManagerRequest;
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
import org.openkilda.messaging.command.switches.InstallGroupRequest;
import org.openkilda.messaging.command.switches.ModifyGroupRequest;
import org.openkilda.messaging.command.switches.PortConfigurationRequest;
import org.openkilda.messaging.command.switches.SwitchRulesDeleteRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.error.rule.FlowCommandErrorData;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.discovery.InstallIslDefaultRulesResult;
import org.openkilda.messaging.info.discovery.RemoveIslDefaultRulesResult;
import org.openkilda.messaging.info.flow.FlowDumpResponse;
import org.openkilda.messaging.info.flow.FlowInstallResponse;
import org.openkilda.messaging.info.flow.FlowReinstallResponse;
import org.openkilda.messaging.info.flow.FlowRemoveResponse;
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
import org.openkilda.messaging.info.switches.ConnectModeResponse;
import org.openkilda.messaging.info.switches.DeleteGroupResponse;
import org.openkilda.messaging.info.switches.DeleteMeterResponse;
import org.openkilda.messaging.info.switches.InstallGroupResponse;
import org.openkilda.messaging.info.switches.ModifyGroupResponse;
import org.openkilda.messaging.info.switches.ModifyMeterResponse;
import org.openkilda.messaging.info.switches.PortConfigurationResponse;
import org.openkilda.messaging.info.switches.PortDescription;
import org.openkilda.messaging.info.switches.SwitchPortsDescription;
import org.openkilda.messaging.info.switches.SwitchRulesResponse;
import org.openkilda.messaging.payload.switches.InstallIslDefaultRulesCommand;
import org.openkilda.messaging.payload.switches.RemoveIslDefaultRulesCommand;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.GroupId;
import org.openkilda.model.MacAddress;
import org.openkilda.model.MeterConfig;
import org.openkilda.model.MeterId;
import org.openkilda.model.MirrorConfig;
import org.openkilda.model.PortStatus;
import org.openkilda.model.SwitchFeature;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.model.cookie.CookieBase.CookieType;
import org.openkilda.model.cookie.FlowSharedSegmentCookie;
import org.openkilda.model.cookie.FlowSharedSegmentCookie.SharedSegmentType;
import org.openkilda.model.cookie.PortColourCookie;
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
import java.util.UUID;
import java.util.stream.Collectors;

class RecordHandler implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(RecordHandler.class);

    private static final UUID EMPTY_COMMAND_ID = new UUID(0, 0);

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
        } else if (data instanceof RemoveFlowForSwitchManagerRequest) {
            doDeleteFlowForSwitchManager(message);
        } else if (data instanceof ModifyFlowMeterForSwitchManagerRequest) {
            doModifyFlowMeterForSwitchManager(message);
        } else if (data instanceof ModifyDefaultMeterForSwitchManagerRequest) {
            doModifyDefaultMeterForSwitchManager(message);
        } else if (data instanceof ReinstallDefaultFlowForSwitchManagerRequest) {
            doReinstallDefaultFlowForSwitchManager(message);
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
        } else if (data instanceof InstallFlowForSwitchManagerRequest) {
            doInstallFlowForSwitchManager(message);
        } else if (data instanceof DeleterMeterForSwitchManagerRequest) {
            doDeleteMeter(message, context.getKafkaSwitchManagerTopic());
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
        } else if (data instanceof InstallIslDefaultRulesCommand) {
            doInstallIslDefaultRule(message);
        } else if (data instanceof RemoveIslDefaultRulesCommand) {
            doRemoveIslDefaultRule(message);
        } else if (data instanceof DumpGroupsForSwitchManagerRequest) {
            doDumpGroupsForSwitchManagerRequest(message);
        } else if (data instanceof DumpGroupsForFlowHsRequest) {
            doDumpGroupsForFlowHsRequest(message);
        } else if (data instanceof InstallGroupRequest) {
            doInstallGroupRequest(message);
        } else if (data instanceof ModifyGroupRequest) {
            doModifyGroupRequest(message);
        } else if (data instanceof DeleteGroupRequest) {
            doDeleteGroupRequest(message);
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
        } else if (payload instanceof ConnectModeRequest) {
            // FIXME(surabujin) - caller do not expect multiple responses(from multiple regions)
            doConnectMode((ConnectModeRequest) payload, message.getCorrelationId());
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

    private void doInstallIslDefaultRule(CommandMessage message) {
        InstallIslDefaultRulesCommand toSetup = (InstallIslDefaultRulesCommand) message.getData();
        InstallIslDefaultRulesResult result = new InstallIslDefaultRulesResult(toSetup.getSrcSwitch(),
                toSetup.getSrcPort(), toSetup.getDstSwitch(), toSetup.getDstPort(), true);
        DatapathId dpid = DatapathId.of(toSetup.getSrcSwitch().toLong());
        try {
            if (toSetup.isMultitableMode()) {
                context.getSwitchManager().installMultitableEndpointIslRules(dpid, toSetup.getSrcPort());
            }
            if (toSetup.isServer42IslRtt()) {
                context.getSwitchManager().installServer42IslRttInputFlow(dpid,
                        toSetup.getServer42Port(), toSetup.getSrcPort());
            }
        } catch (SwitchOperationException e) {
            logger.error("Failed to install isl rules for switch: '{}'", toSetup.getSrcSwitch(), e);
            result.setSuccess(false);
        }

        getKafkaProducer().sendMessageAndTrack(context.getKafkaSwitchManagerTopic(), record.key(),
                new InfoMessage(result, System.currentTimeMillis(), message.getCorrelationId(), context.getRegion()));
    }

    private void doRemoveIslDefaultRule(CommandMessage message) {
        RemoveIslDefaultRulesCommand toRemove = (RemoveIslDefaultRulesCommand) message.getData();
        RemoveIslDefaultRulesResult result = new RemoveIslDefaultRulesResult(toRemove.getSrcSwitch(),
                toRemove.getSrcPort(), toRemove.getDstSwitch(), toRemove.getDstPort(), true);
        DatapathId dpid = DatapathId.of(toRemove.getSrcSwitch().toLong());
        try {
            context.getSwitchManager().removeMultitableEndpointIslRules(dpid, toRemove.getSrcPort());
            context.getSwitchManager().removeServer42IslRttInputFlow(dpid, toRemove.getSrcPort());
        } catch (SwitchOperationException e) {
            logger.error("Failed to remove isl rules for switch: '{}'", toRemove.getSrcSwitch(), e);
            result.setSuccess(false);
        }

        getKafkaProducer().sendMessageAndTrack(context.getKafkaSwitchManagerTopic(), record.key(),
                new InfoMessage(result, System.currentTimeMillis(), message.getCorrelationId(), context.getRegion()));
    }

    private void doDiscoverIslCommand(DiscoverIslCommandData command, String correlationId) {
        context.getDiscoveryEmitter().handleRequest(command, correlationId);
    }

    private void doDiscoverPathCommand(CommandData data) {
        DiscoverPathCommandData command = (DiscoverPathCommandData) data;
        logger.warn("NOT IMPLEMENTED: sending discover Path to {}", command);
    }

    private void installSharedFlow(InstallSharedFlow command) throws SwitchOperationException, FlowCommandException {
        FlowSharedSegmentCookie cookie = new FlowSharedSegmentCookie(command.getCookie());
        SharedSegmentType segmentType = cookie.getSegmentType();
        if (segmentType == SharedSegmentType.QINQ_OUTER_VLAN) {
            context.getSwitchManager().installOuterVlanMatchSharedFlow(command.getSwitchId(), command.getId(), cookie);
        } else {
            throw new FlowCommandException(
                    command.getId(), command.getCookie(), command.getTransactionId(), ErrorType.REQUEST_INVALID,
                    format("Unsupported shared segment type %s (cookie: %s)", segmentType, cookie));
        }
    }

    /**
     * Removes flow.
     *
     * @param message command message for flow deletion
     */
    private void doDeleteFlowForSwitchManager(final CommandMessage message) {
        RemoveFlowForSwitchManagerRequest request = (RemoveFlowForSwitchManagerRequest) message.getData();
        IKafkaProducerService producerService = getKafkaProducer();
        String replyToTopic = context.getKafkaSwitchManagerTopic();
        DatapathId dpid = DatapathId.of(request.getSwitchId().toLong());

        try {
            processDeleteFlow(request.getFlowCommand(), dpid);

            InfoMessage response = new InfoMessage(new FlowRemoveResponse(), System.currentTimeMillis(),
                    message.getCorrelationId());
            producerService.sendMessageAndTrack(replyToTopic, message.getCorrelationId(), response);

        } catch (SwitchOperationException e) {
            logger.error("Failed to process switch rule deletion for switch: '{}'", request.getSwitchId(), e);
            anError(ErrorType.DELETION_FAILURE)
                    .withMessage(e.getMessage())
                    .withDescription(request.getSwitchId().toString())
                    .withCorrelationId(message.getCorrelationId())
                    .withTopic(replyToTopic)
                    .sendVia(producerService);
        }
    }

    private void doModifyFlowMeterForSwitchManager(CommandMessage message) {
        ModifyFlowMeterForSwitchManagerRequest request = (ModifyFlowMeterForSwitchManagerRequest) message.getData();
        IKafkaProducerService producerService = getKafkaProducer();

        long meterId = request.getMeterId();
        SwitchId switchId = request.getSwitchId();
        MeterConfig meterConfig = new MeterConfig(new MeterId(request.getMeterId()), request.getRate());

        logger.info("Modifying flow meter {} on Switch {}", meterId, switchId);
        handleSpeakerCommand(new MeterModifyCommand(new MessageContext(message), switchId, meterConfig));

        InfoMessage response = new InfoMessage(new ModifyMeterResponse(switchId, request.getMeterId()),
                System.currentTimeMillis(), message.getCorrelationId());
        producerService.sendMessageAndTrack(context.getKafkaSwitchManagerTopic(), message.getCorrelationId(), response);
    }

    private void doModifyDefaultMeterForSwitchManager(CommandMessage message) {
        ModifyDefaultMeterForSwitchManagerRequest request =
                (ModifyDefaultMeterForSwitchManagerRequest) message.getData();
        IKafkaProducerService producerService = getKafkaProducer();
        String replyToTopic = context.getKafkaSwitchManagerTopic();

        long meterId = request.getMeterId();
        SwitchId switchId = request.getSwitchId();
        DatapathId dpid = DatapathId.of(switchId.toLong());
        try {
            context.getSwitchManager().modifyDefaultMeter(dpid, request.getMeterId());
            InfoMessage response = new InfoMessage(new ModifyMeterResponse(switchId, request.getMeterId()),
                    System.currentTimeMillis(), message.getCorrelationId());
            producerService.sendMessageAndTrack(replyToTopic, message.getCorrelationId(), response);
        } catch (UnsupportedSwitchOperationException e) {
            logger.warn(format("Skip meter %d modification on switch %s because switch doesn't support meters",
                    meterId, switchId), e);
        } catch (InvalidMeterIdException | OfInstallException | SwitchNotFoundException e) {
            logger.error("Failed to modify meter {} for switch: '{}'", request.getSwitchId(), meterId, e);
            ErrorType errorType;
            if (e instanceof InvalidMeterIdException) {
                errorType = ErrorType.DATA_INVALID;
            } else if (e instanceof SwitchNotFoundException) {
                errorType = ErrorType.NOT_FOUND;
            } else {
                errorType = ErrorType.INTERNAL_ERROR;
            }

            anError(errorType)
                    .withMessage(e.getMessage())
                    .withDescription(request.getSwitchId().toString())
                    .withCorrelationId(message.getCorrelationId())
                    .withTopic(replyToTopic)
                    .sendVia(producerService);
        }
    }

    /**
     * Reinstall default flow.
     *
     * @param message command message for flow deletion
     */
    private void doReinstallDefaultFlowForSwitchManager(CommandMessage message) {
        ReinstallDefaultFlowForSwitchManagerRequest request =
                (ReinstallDefaultFlowForSwitchManagerRequest) message.getData();
        IKafkaProducerService producerService = getKafkaProducer();
        String replyToTopic = context.getKafkaSwitchManagerTopic();

        long cookie = request.getCookie();

        if (!Cookie.isDefaultRule(cookie)) {
            logger.warn("Failed to reinstall default switch rule for switch: '{}'. Rule {} is not default.",
                    request.getSwitchId(), Long.toHexString(cookie));
            anError(ErrorType.DATA_INVALID)
                    .withMessage(format("Failed to reinstall default switch rule for switch %s. Rule %s is not default",
                            request.getSwitchId(), Long.toHexString(cookie)))
                    .withDescription(request.getSwitchId().toString())
                    .withCorrelationId(message.getCorrelationId())
                    .withTopic(replyToTopic)
                    .sendVia(producerService);
        }

        SwitchId switchId = request.getSwitchId();
        DatapathId dpid = DatapathId.of(switchId.toLong());
        try {
            RemoveFlow command = RemoveFlow.builder()
                    .flowId("REMOVE_DEFAULT_FLOW")
                    .cookie(cookie)
                    .switchId(switchId)
                    .build();
            Set<Long> removedFlows = new HashSet<>(processDeleteFlow(command, dpid));

            for (Long removedFlow : removedFlows) {
                Long installedFlow;

                if (request instanceof ReinstallServer42FlowForSwitchManagerRequest) {
                    installedFlow = processInstallServer42Rule((ReinstallServer42FlowForSwitchManagerRequest) request);
                } else {
                    installedFlow = processInstallDefaultFlowByCookie(switchId, removedFlow);
                }

                InfoMessage response = new InfoMessage(new FlowReinstallResponse(removedFlow, installedFlow),
                        System.currentTimeMillis(), message.getCorrelationId());
                producerService.sendMessageAndTrack(replyToTopic, message.getCorrelationId(), response);
            }

        } catch (SwitchOperationException e) {
            logger.error("Failed to reinstall switch rule for switch: '{}'", request.getSwitchId(), e);
            anError(ErrorType.INTERNAL_ERROR)
                    .withMessage(e.getMessage())
                    .withDescription(request.getSwitchId().toString())
                    .withCorrelationId(message.getCorrelationId())
                    .withTopic(replyToTopic)
                    .sendVia(producerService);
        }

    }

    private Long processInstallServer42Rule(ReinstallServer42FlowForSwitchManagerRequest command)
            throws SwitchOperationException {
        ISwitchManager switchManager = context.getSwitchManager();
        DatapathId dpid = DatapathId.of(command.getSwitchId().toLong());
        long cookie = command.getCookie();

        if (cookie == SERVER_42_FLOW_RTT_OUTPUT_VLAN_COOKIE) {
            return switchManager.installServer42FlowRttOutputVlanFlow(
                    dpid, command.getServer42Port(), command.getServer42Vlan(), command.getServer42MacAddress());
        } else if (cookie == SERVER_42_FLOW_RTT_OUTPUT_VXLAN_COOKIE) {
            return switchManager.installServer42FlowRttOutputVxlanFlow(
                    dpid, command.getServer42Port(), command.getServer42Vlan(), command.getServer42MacAddress());
        } else if (new Cookie(cookie).getType() == CookieType.SERVER_42_FLOW_RTT_INPUT) {
            PortColourCookie portColourCookie = new PortColourCookie(cookie);
            int customerPort = portColourCookie.getPortNumber();
            return switchManager.installServer42FlowRttInputFlow(
                    dpid, command.getServer42Port(), customerPort, command.getServer42MacAddress());
        } else if (cookie == SERVER_42_ISL_RTT_OUTPUT_COOKIE
                || new Cookie(cookie).getType() == CookieType.SERVER_42_ISL_RTT_INPUT) {
            return processInstallServer42IslRttRule(command.getSwitchId(), command.getCookie(),
                    command.getServer42Port(), command.getServer42Vlan(), command.getServer42MacAddress());
        } else {
            logger.warn("Skipping the installation of unexpected server 42 switch rule {} for switch {}",
                    Long.toHexString(cookie), command.getSwitchId());
            return null;
        }
    }

    private void processInstallServer42RttRule(InstallServer42Flow command) throws SwitchOperationException {
        ISwitchManager switchManager = context.getSwitchManager();
        DatapathId dpid = DatapathId.of(command.getSwitchId().toLong());
        Cookie cookie = new Cookie(command.getCookie());
        FlowSharedSegmentCookie sharedSegmentCookie = new FlowSharedSegmentCookie(command.getCookie());

        if (command.getCookie() == SERVER_42_FLOW_RTT_OUTPUT_VLAN_COOKIE) {
            switchManager.installServer42FlowRttOutputVlanFlow(
                    dpid, command.getOutputPort(), command.getServer42Vlan(), command.getServer42MacAddress());
        } else if (command.getCookie() == SERVER_42_FLOW_RTT_OUTPUT_VXLAN_COOKIE) {
            switchManager.installServer42FlowRttOutputVxlanFlow(
                    dpid, command.getOutputPort(), command.getServer42Vlan(), command.getServer42MacAddress());
        } else if (cookie.getType() == CookieType.SERVER_42_FLOW_RTT_INPUT) {
            PortColourCookie portColourCookie = new PortColourCookie(command.getCookie());
            int customerPort = portColourCookie.getPortNumber();
            switchManager.installServer42FlowRttInputFlow(
                    dpid, command.getInputPort(), customerPort, command.getServer42MacAddress());
        } else if (cookie.getType() == CookieType.SHARED_OF_FLOW
                && sharedSegmentCookie.getSegmentType() == SharedSegmentType.SERVER42_QINQ_OUTER_VLAN) {
            switchManager.installServer42OuterVlanMatchSharedFlow(dpid, sharedSegmentCookie);
        } else if (command.getCookie() == SERVER_42_ISL_RTT_OUTPUT_COOKIE) {
            processInstallServer42IslRttRule(command.getSwitchId(), command.getCookie(), command.getOutputPort(),
                    command.getServer42Vlan(), command.getServer42MacAddress());
        } else if (cookie.getType() == CookieType.SERVER_42_ISL_RTT_INPUT) {
            processInstallServer42IslRttRule(command.getSwitchId(), command.getCookie(), command.getInputPort(),
                    command.getServer42Vlan(), command.getServer42MacAddress());
        } else {
            logger.warn("Skipping the installation of unexpected server 42 switch rule {} for switch {}",
                    Long.toHexString(command.getCookie()), command.getSwitchId());
        }
    }

    private Long processInstallServer42IslRttRule(SwitchId switchId, long cookie, int server42Port, int server42Vlan,
                                                  MacAddress server42MacAddress) throws SwitchOperationException {
        ISwitchManager switchManager = context.getSwitchManager();
        DatapathId dpid = DatapathId.of(switchId.toLong());

        if (cookie == SERVER_42_ISL_RTT_OUTPUT_COOKIE) {
            return switchManager.installServer42IslRttOutputFlow(dpid, server42Port, server42Vlan, server42MacAddress);
        } else if (new Cookie(cookie).getType() == CookieType.SERVER_42_ISL_RTT_INPUT) {
            PortColourCookie portColourCookie = new PortColourCookie(cookie);
            int islPort = portColourCookie.getPortNumber();
            return switchManager.installServer42IslRttInputFlow(dpid, server42Port, islPort);
        } else {
            logger.warn("Skipping the installation of unexpected server 42 switch rule {} for switch {}",
                    Long.toHexString(cookie), switchId);
            return null;
        }
    }

    private Long processInstallDefaultFlowByCookie(SwitchId switchId, long cookie) throws SwitchOperationException {
        ISwitchManager switchManager = context.getSwitchManager();
        DatapathId dpid = DatapathId.of(switchId.toLong());

        Cookie encodedCookie = new Cookie(cookie);
        PortColourCookie portColourCookie = new PortColourCookie(cookie);
        CookieType cookieType = encodedCookie.getType();
        if (cookie == DROP_RULE_COOKIE) {
            return switchManager.installDropFlow(dpid);
        } else if (cookie == VERIFICATION_BROADCAST_RULE_COOKIE) {
            return switchManager.installVerificationRule(dpid, true);
        } else if (cookie == VERIFICATION_UNICAST_RULE_COOKIE) {
            return switchManager.installVerificationRule(dpid, false);
        } else if (cookie == DROP_VERIFICATION_LOOP_RULE_COOKIE) {
            return switchManager.installDropLoopRule(dpid);
        } else if (cookie == CATCH_BFD_RULE_COOKIE) {
            return switchManager.installBfdCatchFlow(dpid);
        } else if (cookie == ROUND_TRIP_LATENCY_RULE_COOKIE) {
            return switchManager.installRoundTripLatencyFlow(dpid);
        } else if (cookie == VERIFICATION_UNICAST_VXLAN_RULE_COOKIE) {
            return switchManager.installUnicastVerificationRuleVxlan(dpid);
        } else if (cookie == MULTITABLE_PRE_INGRESS_PASS_THROUGH_COOKIE) {
            return switchManager.installPreIngressTablePassThroughDefaultRule(dpid);
        } else if (cookie == MULTITABLE_INGRESS_DROP_COOKIE) {
            return switchManager.installDropFlowForTable(dpid, INGRESS_TABLE_ID, MULTITABLE_INGRESS_DROP_COOKIE);
        } else if (cookie == MULTITABLE_POST_INGRESS_DROP_COOKIE) {
            return switchManager.installDropFlowForTable(dpid, POST_INGRESS_TABLE_ID,
                    MULTITABLE_POST_INGRESS_DROP_COOKIE);
        } else if (cookie == MULTITABLE_EGRESS_PASS_THROUGH_COOKIE) {
            return switchManager.installEgressTablePassThroughDefaultRule(dpid);
        } else if (cookie == MULTITABLE_TRANSIT_DROP_COOKIE) {
            return switchManager.installDropFlowForTable(dpid, TRANSIT_TABLE_ID,
                    MULTITABLE_TRANSIT_DROP_COOKIE);
        } else if (cookie == LLDP_INPUT_PRE_DROP_COOKIE) {
            return switchManager.installLldpInputPreDropFlow(dpid);
        } else if (cookie == LLDP_INGRESS_COOKIE) {
            return switchManager.installLldpIngressFlow(dpid);
        } else if (cookie == LLDP_POST_INGRESS_COOKIE) {
            return switchManager.installLldpPostIngressFlow(dpid);
        } else if (cookie == LLDP_POST_INGRESS_VXLAN_COOKIE) {
            return switchManager.installLldpPostIngressVxlanFlow(dpid);
        } else if (cookie == LLDP_POST_INGRESS_ONE_SWITCH_COOKIE) {
            return switchManager.installLldpPostIngressOneSwitchFlow(dpid);
        } else if (cookie == LLDP_TRANSIT_COOKIE) {
            return switchManager.installLldpTransitFlow(dpid);
        } else if (cookie == ARP_INPUT_PRE_DROP_COOKIE) {
            return switchManager.installArpInputPreDropFlow(dpid);
        } else if (cookie == ARP_INGRESS_COOKIE) {
            return switchManager.installArpIngressFlow(dpid);
        } else if (cookie == ARP_POST_INGRESS_COOKIE) {
            return switchManager.installArpPostIngressFlow(dpid);
        } else if (cookie == ARP_POST_INGRESS_VXLAN_COOKIE) {
            return switchManager.installArpPostIngressVxlanFlow(dpid);
        } else if (cookie == ARP_POST_INGRESS_ONE_SWITCH_COOKIE) {
            return switchManager.installArpPostIngressOneSwitchFlow(dpid);
        } else if (cookie == ARP_TRANSIT_COOKIE) {
            return switchManager.installArpTransitFlow(dpid);
        } else if (cookie == SERVER_42_FLOW_RTT_TURNING_COOKIE) {
            return switchManager.installServer42FlowRttTurningFlow(dpid);
        } else if (cookie == SERVER_42_ISL_RTT_TURNING_COOKIE) {
            return switchManager.installServer42IslRttTurningFlow(dpid);
        } else if (cookie == SERVER_42_FLOW_RTT_VXLAN_TURNING_COOKIE) {
            return switchManager.installServer42FlowRttVxlanTurningFlow(dpid);
        } else if (cookieType == CookieType.MULTI_TABLE_INGRESS_RULES) {
            return switchManager.installIntermediateIngressRule(dpid, portColourCookie.getPortNumber());
        } else if (cookieType == CookieType.MULTI_TABLE_ISL_VLAN_EGRESS_RULES) {
            return switchManager.installEgressIslVlanRule(dpid, portColourCookie.getPortNumber());
        } else if (cookieType == CookieType.MULTI_TABLE_ISL_VXLAN_TRANSIT_RULES) {
            return switchManager.installTransitIslVxlanRule(dpid, portColourCookie.getPortNumber());
        } else if (cookieType == CookieType.MULTI_TABLE_ISL_VXLAN_EGRESS_RULES) {
            return switchManager.installEgressIslVxlanRule(dpid, portColourCookie.getPortNumber());
        } else if (cookieType == CookieType.LLDP_INPUT_CUSTOMER_TYPE) {
            return switchManager.installLldpInputCustomerFlow(dpid, portColourCookie.getPortNumber());
        } else if (cookieType == CookieType.ARP_INPUT_CUSTOMER_TYPE) {
            return switchManager.installArpInputCustomerFlow(dpid, portColourCookie.getPortNumber());
        } else if (cookie == SERVER_42_FLOW_RTT_VXLAN_TURNING_COOKIE) {
            return switchManager.installServer42FlowRttVxlanTurningFlow(dpid);
        } else {
            logger.warn("Skipping the installation of unexpected default switch rule {} for switch {}",
                    encodedCookie, switchId);
        }
        return null;
    }

    private List<Long> processDeleteFlow(RemoveFlow command, DatapathId dpid) throws SwitchOperationException {
        logger.info("Deleting flow {} from switch {}", command.getId(), dpid);

        if (command.isCleanUpIngress()) {
            context.getSwitchManager().removeIntermediateIngressRule(dpid, command.getCriteria().getInPort());
        }
        if (command.isCleanUpIngressLldp()) {
            context.getSwitchManager().removeLldpInputCustomerFlow(dpid, command.getCriteria().getInPort());
        }
        if (command.isCleanUpIngressArp()) {
            context.getSwitchManager().removeArpInputCustomerFlow(dpid, command.getCriteria().getInPort());
        }
        DeleteRulesCriteria criteria = Optional.ofNullable(command.getCriteria())
                .orElseGet(() -> DeleteRulesCriteria.builder().cookie(command.getCookie()).build());
        ISwitchManager switchManager = context.getSwitchManager();
        List<Long> cookiesOfRemovedRules = switchManager.deleteRulesByCriteria(dpid, command.isMultiTable(),
                command.getRuleType(), criteria);
        if (cookiesOfRemovedRules.isEmpty()) {
            logger.warn("No rules were removed by criteria {} for flow {} from switch {}",
                    criteria, command.getId(), dpid);
        }

        Long meterId = command.getMeterId();
        if (meterId != null) {
            try {
                switchManager.deleteMeter(dpid, meterId);
            } catch (UnsupportedOperationException e) {
                logger.info("Skip meter {} deletion from switch {}: {}", meterId, dpid, e.getMessage());
            } catch (SwitchOperationException e) {
                logger.error("Failed to delete meter {} from switch {}: {}", meterId, dpid, e.getMessage());
            }
        }
        return cookiesOfRemovedRules;
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
                removedRules.addAll(switchManager.deleteRulesByCriteria(dpid, false, null, criteria));
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

    private void doConnectMode(ConnectModeRequest request, String correlationId) {
        if (request.getMode() != null) {
            logger.debug("Setting CONNECT MODE to '{}'", request.getMode());
        } else {
            logger.debug("Getting CONNECT MODE");
        }

        ISwitchManager switchManager = context.getSwitchManager();
        ConnectModeRequest.Mode result = switchManager.connectMode(request.getMode());

        logger.info("CONNECT MODE is now '{}'", result);
        ConnectModeResponse response = new ConnectModeResponse(result);
        InfoMessage infoMessage = new InfoMessage(response, System.currentTimeMillis(), correlationId);
        getKafkaProducer().sendMessageAndTrack(context.getKafkaNorthboundTopic(), infoMessage);
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

    private void doInstallGroupRequest(CommandMessage message) {
        SwitchId switchId = ((InstallGroupRequest) message.getData()).getSwitchId();
        MirrorConfig mirrorConfig = ((InstallGroupRequest) message.getData()).getMirrorConfig();
        FlowTransitEncapsulation encapsulation = ((InstallGroupRequest) message.getData()).getEncapsulation();
        SwitchId egressSwitchId = ((InstallGroupRequest) message.getData()).getEgressSwitchId();

        FlowTransitData flowTransitData = null;
        if (encapsulation != null) {
            flowTransitData = FlowTransitData.builder()
                    .ingressSwitchId(switchId)
                    .egressSwitchId(egressSwitchId)
                    .encapsulation(encapsulation)
                    .build();
        }

        logger.debug("Install group '{}' for switch '{}'", mirrorConfig.getGroupId().intValue(), switchId);
        handleSpeakerCommand(new GroupInstallCommand(
                new MessageContext(message), switchId, mirrorConfig, flowTransitData));

        InstallGroupResponse response = new InstallGroupResponse(switchId, mirrorConfig.getGroupId().intValue());

        String correlationId = message.getCorrelationId();
        InfoMessage infoMessage = new InfoMessage(response, System.currentTimeMillis(), correlationId);
        getKafkaProducer().sendMessageAndTrack(context.getKafkaSwitchManagerTopic(), correlationId, infoMessage);
    }

    private void doModifyGroupRequest(CommandMessage message) {
        SwitchId switchId = ((ModifyGroupRequest) message.getData()).getSwitchId();
        MirrorConfig mirrorConfig = ((ModifyGroupRequest) message.getData()).getMirrorConfig();
        FlowTransitEncapsulation encapsulation = ((ModifyGroupRequest) message.getData()).getEncapsulation();
        SwitchId egressSwitchId = ((ModifyGroupRequest) message.getData()).getEgressSwitchId();

        FlowTransitData flowTransitData = null;
        if (encapsulation != null) {
            flowTransitData = FlowTransitData.builder()
                    .ingressSwitchId(switchId)
                    .egressSwitchId(egressSwitchId)
                    .encapsulation(encapsulation)
                    .build();
        }

        logger.debug("Modify group '{}' for switch '{}'", mirrorConfig.getGroupId().intValue(), switchId);
        handleSpeakerCommand(new GroupModifyCommand(
                new MessageContext(message), switchId, mirrorConfig, flowTransitData));

        ModifyGroupResponse response = new ModifyGroupResponse(switchId, mirrorConfig.getGroupId().intValue());

        String correlationId = message.getCorrelationId();
        InfoMessage infoMessage = new InfoMessage(response, System.currentTimeMillis(), correlationId);
        getKafkaProducer().sendMessageAndTrack(context.getKafkaSwitchManagerTopic(), correlationId, infoMessage);
    }

    private void doDeleteGroupRequest(CommandMessage message) {
        SwitchId switchId = ((DeleteGroupRequest) message.getData()).getSwitchId();
        GroupId groupId = ((DeleteGroupRequest) message.getData()).getGroupId();

        logger.debug("Delete group '{}' for switch '{}'", groupId, switchId);
        handleSpeakerCommand(new GroupRemoveCommand(new MessageContext(message), switchId, groupId));

        DeleteGroupResponse response = new DeleteGroupResponse(true);

        String correlationId = message.getCorrelationId();
        InfoMessage infoMessage = new InfoMessage(response, System.currentTimeMillis(), correlationId);
        getKafkaProducer().sendMessageAndTrack(context.getKafkaSwitchManagerTopic(), correlationId, infoMessage);
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

    /**
     * Install of flow on the switch from SwitchManager topology.
     *
     * @param message with list of flows.
     */
    private void doInstallFlowForSwitchManager(final CommandMessage message) {
        InstallFlowForSwitchManagerRequest request = (InstallFlowForSwitchManagerRequest) message.getData();

        String replyToTopic = context.getKafkaSwitchManagerTopic();
        FlowSegmentResponseFactory responseFactory = new FlowSegmentSyncResponseFactory(
                message.getCorrelationId(), replyToTopic);
        MessageContext messageContext = new MessageContext(message);
        Optional<FlowSegmentWrapperCommand> syncCommand = makeSyncCommand(
                request.getFlowCommand(), messageContext, responseFactory);
        if (syncCommand.isPresent()) {
            handleSpeakerCommand(syncCommand.get());
            return;
        }

        try {
            installFlow(request.getFlowCommand());

        } catch (SwitchOperationException e) {
            logger.error("Error during flow installation", e);
            ErrorData errorData = new ErrorData(ErrorType.INTERNAL_ERROR, "Error during flow installation",
                    "Switch operation error");
            ErrorMessage error = new ErrorMessage(errorData, System.currentTimeMillis(),
                    message.getCorrelationId());
            getKafkaProducer().sendMessageAndTrack(replyToTopic, message.getCorrelationId(), error);

        } catch (FlowCommandException e) {
            String errorMessage = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            logger.error("Failed to handle message {}: {}", message, errorMessage);
            ErrorData errorData = new FlowCommandErrorData(e.getFlowId(), e.getCookie(), e.getTransactionId(),
                    e.getErrorType(), errorMessage, e.getMessage());
            ErrorMessage error = new ErrorMessage(errorData, System.currentTimeMillis(),
                    message.getCorrelationId());
            getKafkaProducer().sendMessageAndTrack(replyToTopic, message.getCorrelationId(), error);
        }

        InfoMessage response = new InfoMessage(new FlowInstallResponse(), System.currentTimeMillis(),
                message.getCorrelationId());
        getKafkaProducer().sendMessageAndTrack(replyToTopic, message.getCorrelationId(), response);
    }

    private void installFlow(BaseFlow command) throws FlowCommandException,
            SwitchOperationException {
        logger.debug("Processing flow install command {}", command);
        if (command instanceof InstallServer42Flow) {
            processInstallServer42RttRule((InstallServer42Flow) command);
        } else if (Cookie.isDefaultRule(command.getCookie())) {
            processInstallDefaultFlowByCookie(command.getSwitchId(), command.getCookie());
        } else if (command instanceof InstallSharedFlow) {
            installSharedFlow((InstallSharedFlow) command);
        } else {
            throw new FlowCommandException(command.getId(), command.getCookie(), command.getTransactionId(),
                    ErrorType.REQUEST_INVALID, "Unsupported command for install.");
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
        if (handleRuleManagerCommand()) {
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

    private Optional<FlowSegmentWrapperCommand> makeSyncCommand(
            BaseFlow request, MessageContext messageContext, FlowSegmentResponseFactory responseFactory) {
        FlowSegmentWrapperCommand command;
        if (request instanceof InstallIngressMirrorFlow) {
            command = makeFlowSegmentWrappedCommand(
                    (InstallIngressMirrorFlow) request, messageContext, responseFactory);
        } else if (request instanceof InstallIngressFlow) {
            command = makeFlowSegmentWrappedCommand((InstallIngressFlow) request, messageContext, responseFactory);
        } else if (request instanceof InstallOneSwitchMirrorFlow) {
            command = makeFlowSegmentWrappedCommand(
                    (InstallOneSwitchMirrorFlow) request, messageContext, responseFactory);
        } else if (request instanceof InstallOneSwitchFlow) {
            command = makeFlowSegmentWrappedCommand((InstallOneSwitchFlow) request, messageContext, responseFactory);
        } else if (request instanceof InstallIngressLoopFlow) {
            command = makeIngressLoopWrappedCommand((InstallIngressLoopFlow) request, messageContext, responseFactory);
        } else if (request instanceof InstallTransitLoopFlow) {
            command = makeTransitLoopWrappedCommand((InstallTransitLoopFlow) request, messageContext, responseFactory);
        } else if (request instanceof InstallEgressMirrorFlow) {
            command = makeFlowSegmentWrappedCommand((InstallEgressMirrorFlow) request, messageContext, responseFactory);
        } else if (request instanceof InstallEgressFlow) {
            command = makeFlowSegmentWrappedCommand((InstallEgressFlow) request, messageContext, responseFactory);
        } else if (request instanceof InstallServer42IngressFlow) {
            command = makeFlowSegmentWrappedCommand(
                    (InstallServer42IngressFlow) request, messageContext, responseFactory);
        } else if (request instanceof InstallTransitFlow) {
            command = makeFlowSegmentWrappedCommand((InstallTransitFlow) request, messageContext, responseFactory);
        } else {
            command = null;
        }
        return Optional.ofNullable(command);
    }

    private FlowSegmentWrapperCommand makeFlowSegmentWrappedCommand(
            InstallServer42IngressFlow request, MessageContext messageContext,
            FlowSegmentResponseFactory responseFactory) {
        IngressServer42FlowInstallCommand command = new IngressServer42FlowInstallCommand(
                messageContext, EMPTY_COMMAND_ID, makeSegmentMetadata(request), request.getSwitchId(),
                request.getCustomerPort(), request.getInputVlanId(), request.getInputInnerVlanId(),
                request.getEgressSwitchId(), request.getOutputPort(), makeTransitEncapsulation(request),
                request.getInputPort(), request.getServer42MacAddress());
        return new FlowSegmentWrapperCommand(command, responseFactory);
    }

    private FlowSegmentWrapperCommand makeFlowSegmentWrappedCommand(
            InstallIngressMirrorFlow request, MessageContext messageContext,
            FlowSegmentResponseFactory responseFactory) {
        FlowEndpoint endpoint = new FlowEndpoint(
                request.getSwitchId(), request.getInputPort(), request.getInputVlanId(), request.getInputInnerVlanId(),
                request.isEnableLldp(), request.isEnableArp());
        MeterConfig meterConfig = makeMeterConfig(request.getMeterId(), request.getBandwidth());
        IngressMirrorFlowSegmentInstallCommand command = new IngressMirrorFlowSegmentInstallCommand(
                messageContext, EMPTY_COMMAND_ID, makeSegmentMetadata(request), endpoint, meterConfig,
                request.getEgressSwitchId(), request.getOutputPort(), makeTransitEncapsulation(request),
                new RulesContext(), request.getMirrorConfig());

        return new FlowSegmentWrapperCommand(command, responseFactory);
    }

    private FlowSegmentWrapperCommand makeFlowSegmentWrappedCommand(
            InstallIngressFlow request, MessageContext messageContext, FlowSegmentResponseFactory responseFactory) {
        FlowEndpoint endpoint = new FlowEndpoint(
                request.getSwitchId(), request.getInputPort(), request.getInputVlanId(), request.getInputInnerVlanId(),
                request.isEnableLldp(), request.isEnableArp());
        MeterConfig meterConfig = makeMeterConfig(request.getMeterId(), request.getBandwidth());
        IngressFlowSegmentInstallCommand command = new IngressFlowSegmentInstallCommand(
                messageContext, EMPTY_COMMAND_ID, makeSegmentMetadata(request), endpoint, meterConfig,
                request.getEgressSwitchId(), request.getOutputPort(), makeTransitEncapsulation(request),
                new RulesContext(), request.getMirrorConfig());

        return new FlowSegmentWrapperCommand(command, responseFactory);
    }

    private FlowSegmentWrapperCommand makeFlowSegmentWrappedCommand(
            InstallOneSwitchMirrorFlow request, MessageContext messageContext,
            FlowSegmentResponseFactory responseFactory) {
        FlowEndpoint endpoint = new FlowEndpoint(
                request.getSwitchId(), request.getInputPort(), request.getInputVlanId(), request.getInputInnerVlanId(),
                request.isEnableLldp(), request.isEnableArp());
        FlowEndpoint egressEndpoint = new FlowEndpoint(
                request.getSwitchId(), request.getOutputPort(), request.getOutputVlanId(),
                request.getOutputInnerVlanId());
        MeterConfig meterConfig = makeMeterConfig(request.getMeterId(), request.getBandwidth());
        OneSwitchMirrorFlowInstallCommand command = new OneSwitchMirrorFlowInstallCommand(
                messageContext, EMPTY_COMMAND_ID, makeSegmentMetadata(request), endpoint, meterConfig, egressEndpoint,
                new RulesContext(), request.getMirrorConfig());

        return new FlowSegmentWrapperCommand(command, responseFactory);
    }

    private FlowSegmentWrapperCommand makeFlowSegmentWrappedCommand(
            InstallOneSwitchFlow request, MessageContext messageContext, FlowSegmentResponseFactory responseFactory) {
        FlowEndpoint endpoint = new FlowEndpoint(
                request.getSwitchId(), request.getInputPort(), request.getInputVlanId(), request.getInputInnerVlanId(),
                request.isEnableLldp(), request.isEnableArp());
        FlowEndpoint egressEndpoint = new FlowEndpoint(
                request.getSwitchId(), request.getOutputPort(), request.getOutputVlanId(),
                request.getOutputInnerVlanId());
        MeterConfig meterConfig = makeMeterConfig(request.getMeterId(), request.getBandwidth());
        OneSwitchFlowInstallCommand command = new OneSwitchFlowInstallCommand(
                messageContext, EMPTY_COMMAND_ID, makeSegmentMetadata(request), endpoint, meterConfig, egressEndpoint,
                new RulesContext(), request.getMirrorConfig());

        return new FlowSegmentWrapperCommand(command, responseFactory);
    }

    private FlowSegmentWrapperCommand makeFlowSegmentWrappedCommand(
            InstallEgressMirrorFlow request, MessageContext messageContext,
            FlowSegmentResponseFactory responseFactory) {
        FlowEndpoint endpoint = new FlowEndpoint(
                request.getSwitchId(), request.getOutputPort(), request.getOutputVlanId(),
                request.getOutputInnerVlanId());
        EgressMirrorFlowSegmentInstallCommand command = new EgressMirrorFlowSegmentInstallCommand(
                messageContext, EMPTY_COMMAND_ID, makeSegmentMetadata(request), endpoint, request.getIngressEndpoint(),
                request.getInputPort(), makeTransitEncapsulation(request), request.getMirrorConfig());

        return new FlowSegmentWrapperCommand(command, responseFactory);
    }

    private FlowSegmentWrapperCommand makeFlowSegmentWrappedCommand(
            InstallEgressFlow request, MessageContext messageContext, FlowSegmentResponseFactory responseFactory) {
        FlowEndpoint endpoint = new FlowEndpoint(
                request.getSwitchId(), request.getOutputPort(), request.getOutputVlanId(),
                request.getOutputInnerVlanId());
        EgressFlowSegmentInstallCommand command = new EgressFlowSegmentInstallCommand(
                messageContext, EMPTY_COMMAND_ID, makeSegmentMetadata(request), endpoint, request.getIngressEndpoint(),
                request.getInputPort(), makeTransitEncapsulation(request), request.getMirrorConfig());

        return new FlowSegmentWrapperCommand(command, responseFactory);
    }

    private FlowSegmentWrapperCommand makeFlowSegmentWrappedCommand(
            InstallTransitFlow request, MessageContext messageContext, FlowSegmentResponseFactory responseFactory) {
        TransitFlowSegmentInstallCommand command = new TransitFlowSegmentInstallCommand(
                messageContext, request.getSwitchId(), EMPTY_COMMAND_ID, makeSegmentMetadata(request),
                request.getInputPort(), makeTransitEncapsulation(request), request.getOutputPort(), null);

        return new FlowSegmentWrapperCommand(command, responseFactory);
    }

    private FlowSegmentWrapperCommand makeTransitLoopWrappedCommand(
            InstallTransitLoopFlow request, MessageContext messageContext, FlowSegmentResponseFactory responseFactory) {
        TransitFlowLoopSegmentInstallCommand command = new TransitFlowLoopSegmentInstallCommand(
                messageContext, request.getSwitchId(), request.getIngressEndpoint().getSwitchId(),
                EMPTY_COMMAND_ID, makeSegmentMetadata(request), request.getInputPort(),
                makeTransitEncapsulation(request), request.getOutputPort());

        return new FlowSegmentWrapperCommand(command, responseFactory);
    }

    private FlowSegmentWrapperCommand makeIngressLoopWrappedCommand(
            InstallIngressLoopFlow request, MessageContext messageContext, FlowSegmentResponseFactory responseFactory) {
        IngressFlowLoopSegmentInstallCommand command = new IngressFlowLoopSegmentInstallCommand(
                messageContext, EMPTY_COMMAND_ID, makeSegmentMetadata(request), request.getIngressEndpoint());

        return new FlowSegmentWrapperCommand(command, responseFactory);
    }

    private MeterConfig makeMeterConfig(Long rawId, long bandwidth) {
        if (rawId == null) {
            return null;
        }
        return new MeterConfig(new MeterId(rawId), bandwidth);
    }

    private FlowSegmentMetadata makeSegmentMetadata(BaseInstallFlow request) {
        Cookie cookie = new Cookie(request.getCookie());
        return new FlowSegmentMetadata(request.getId(), cookie, request.isMultiTable());
    }

    private FlowTransitEncapsulation makeTransitEncapsulation(InstallTransitFlow request) {
        return new FlowTransitEncapsulation(request.getTransitEncapsulationId(), request.getTransitEncapsulationType());
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

    private boolean handleRuleManagerCommand() {
        try {
            BaseSpeakerCommandsRequest request = MAPPER.readValue(record.value(), BaseSpeakerCommandsRequest.class);
            handleRuleManagerCommand(request);
            return true;
        } catch (JsonMappingException e) {
            logger.trace("Received deprecated command message");
        } catch (IOException e) {
            logger.error("Error while parsing record {}", record.value(), e);
        }
        return false;
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
