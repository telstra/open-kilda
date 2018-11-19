/* Copyright 2017 Telstra Open Source
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

import static java.util.Arrays.asList;
import static org.openkilda.messaging.Utils.MAPPER;

import org.openkilda.floodlight.command.CommandContext;
import org.openkilda.floodlight.command.ping.PingRequestCommand;
import org.openkilda.floodlight.converter.OfFlowStatsConverter;
import org.openkilda.floodlight.converter.OfPortDescConverter;
import org.openkilda.floodlight.error.FlowCommandException;
import org.openkilda.floodlight.error.SwitchNotFoundException;
import org.openkilda.floodlight.error.SwitchOperationException;
import org.openkilda.floodlight.service.CommandProcessorService;
import org.openkilda.floodlight.service.kafka.IKafkaProducerService;
import org.openkilda.floodlight.switchmanager.ISwitchManager;
import org.openkilda.floodlight.switchmanager.MeterPool;
import org.openkilda.floodlight.switchmanager.SwitchTrackingService;
import org.openkilda.floodlight.utils.CorrelationContext;
import org.openkilda.floodlight.utils.CorrelationContext.CorrelationContextClosable;
import org.openkilda.messaging.Destination;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.CommandWithReplyToMessage;
import org.openkilda.messaging.command.discovery.DiscoverIslCommandData;
import org.openkilda.messaging.command.discovery.DiscoverPathCommandData;
import org.openkilda.messaging.command.discovery.NetworkCommandData;
import org.openkilda.messaging.command.discovery.PortsCommandData;
import org.openkilda.messaging.command.flow.BaseInstallFlow;
import org.openkilda.messaging.command.flow.BatchInstallRequest;
import org.openkilda.messaging.command.flow.DeleteMeterRequest;
import org.openkilda.messaging.command.flow.InstallEgressFlow;
import org.openkilda.messaging.command.flow.InstallIngressFlow;
import org.openkilda.messaging.command.flow.InstallOneSwitchFlow;
import org.openkilda.messaging.command.flow.InstallTransitFlow;
import org.openkilda.messaging.command.flow.RemoveFlow;
import org.openkilda.messaging.command.switches.ConnectModeRequest;
import org.openkilda.messaging.command.switches.DeleteRulesAction;
import org.openkilda.messaging.command.switches.DeleteRulesCriteria;
import org.openkilda.messaging.command.switches.DumpPortDescriptionRequest;
import org.openkilda.messaging.command.switches.DumpRulesRequest;
import org.openkilda.messaging.command.switches.DumpSwitchPortsDescriptionRequest;
import org.openkilda.messaging.command.switches.InstallRulesAction;
import org.openkilda.messaging.command.switches.PortConfigurationRequest;
import org.openkilda.messaging.command.switches.SwitchRulesDeleteRequest;
import org.openkilda.messaging.command.switches.SwitchRulesInstallRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.error.rule.DumpRulesErrorData;
import org.openkilda.messaging.floodlight.request.PingRequest;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.discovery.DiscoPacketSendingConfirmation;
import org.openkilda.messaging.info.event.PortChangeType;
import org.openkilda.messaging.info.rule.FlowEntry;
import org.openkilda.messaging.info.rule.SwitchFlowEntries;
import org.openkilda.messaging.info.stats.PortStatus;
import org.openkilda.messaging.info.stats.SwitchPortStatusData;
import org.openkilda.messaging.info.switches.ConnectModeResponse;
import org.openkilda.messaging.info.switches.DeleteMeterResponse;
import org.openkilda.messaging.info.switches.PortConfigurationResponse;
import org.openkilda.messaging.info.switches.PortDescription;
import org.openkilda.messaging.info.switches.SwitchPortsDescription;
import org.openkilda.messaging.info.switches.SwitchRulesResponse;
import org.openkilda.messaging.model.NetworkEndpoint;
import org.openkilda.messaging.model.SwitchId;
import org.openkilda.messaging.payload.flow.OutputVlanType;

import net.floodlightcontroller.core.IOFSwitch;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.projectfloodlight.openflow.protocol.OFFlowStatsEntry;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private final ConsumerRecord<String, String> record;
    private final MeterPool meterPool;

    private final CommandProcessorService commandProcessor;

    public RecordHandler(ConsumerContext context, ConsumerRecord<String, String> record,
                         MeterPool meterPool) {
        this.context = context;
        this.record = record;
        this.meterPool = meterPool;

        this.commandProcessor = context.getModuleContext().getServiceImpl(CommandProcessorService.class);
    }

    protected void doControllerMsg(CommandMessage message) {
        // Define the destination topic where the reply will be sent to.
        final String replyToTopic;
        if (message instanceof CommandWithReplyToMessage) {
            replyToTopic = ((CommandWithReplyToMessage) message).getReplyTo();
        } else {
            replyToTopic = context.getKafkaFlowTopic();
        }
        final Destination replyDestination = getDestinationForTopic(replyToTopic);

        try {
            handleCommand(message, replyToTopic, replyDestination);
        } catch (SwitchOperationException e) {
            logger.error("Unable to handle request {}: {}", message.getData().getClass().getName(), e.getMessage());
        } catch (FlowCommandException e) {
            ErrorMessage error = new ErrorMessage(
                    e.makeErrorResponse(),
                    System.currentTimeMillis(), message.getCorrelationId(), replyDestination);
            getKafkaProducer().sendMessageAndTrack(replyToTopic, error);
        } catch (Exception e) {
            logger.error("Unhandled exception", e);
        }
    }

    private void handleCommand(CommandMessage message, String replyToTopic, Destination replyDestination)
            throws FlowCommandException, SwitchOperationException {
        CommandData data = message.getData();
        CommandContext context = new CommandContext(this.context.getModuleContext(), message.getCorrelationId());

        if (data instanceof DiscoverIslCommandData) {
            doDiscoverIslCommand(message);
        } else if (data instanceof PingRequest) {
            doPingRequest(context, (PingRequest) data);
        } else if (data instanceof DiscoverPathCommandData) {
            doDiscoverPathCommand(data);
        } else if (data instanceof InstallIngressFlow) {
            doProcessIngressFlow(message, replyToTopic, replyDestination);
        } else if (data instanceof InstallEgressFlow) {
            doProcessEgressFlow(message, replyToTopic, replyDestination);
        } else if (data instanceof InstallTransitFlow) {
            doProcessTransitFlow(message, replyToTopic, replyDestination);
        } else if (data instanceof InstallOneSwitchFlow) {
            doProcessOneSwitchFlow(message, replyToTopic, replyDestination);
        } else if (data instanceof RemoveFlow) {
            doDeleteFlow(message, replyToTopic, replyDestination);
        } else if (data instanceof NetworkCommandData) {
            doNetworkDump(message);
        } else if (data instanceof SwitchRulesDeleteRequest) {
            doDeleteSwitchRules(message, replyToTopic, replyDestination);
        } else if (data instanceof SwitchRulesInstallRequest) {
            doInstallSwitchRules(message, replyToTopic, replyDestination);
        } else if (data instanceof ConnectModeRequest) {
            doConnectMode(message, replyToTopic, replyDestination);
        } else if (data instanceof DumpRulesRequest) {
            doDumpRulesRequest(message, replyToTopic, replyDestination);
        } else if (data instanceof BatchInstallRequest) {
            doBatchInstall(message);
        } else if (data instanceof PortsCommandData) {
            doPortsCommandDataRequest(message);
        } else if (data instanceof DeleteMeterRequest) {
            doDeleteMeter(message, replyToTopic, replyDestination);
        } else if (data instanceof PortConfigurationRequest) {
            doConfigurePort(message, replyToTopic, replyDestination);
        } else if (data instanceof DumpSwitchPortsDescriptionRequest) {
            doDumpSwitchPortsDescriptionRequest(message, replyToTopic, replyDestination);
        } else if (data instanceof DumpPortDescriptionRequest) {
            doDumpPortDescriptionRequest(message, replyToTopic, replyDestination);
        } else {
            logger.error("unknown data type: {}", data.toString());
        }
    }

    private Destination getDestinationForTopic(String replyToTopic) {
        //TODO: depending on the future system design, either get rid of destination or complete the switch-case.
        if (context.getKafkaNorthboundTopic().equals(replyToTopic)) {
            return Destination.NORTHBOUND;
        } else {
            return Destination.WFM_TRANSACTION;
        }
    }

    private void doDiscoverIslCommand(CommandMessage message) {
        DiscoverIslCommandData command = (DiscoverIslCommandData) message.getData();
        SwitchId switchId = command.getSwitchId();
        context.getPathVerificationService().sendDiscoveryMessage(
                DatapathId.of(switchId.toLong()), OFPort.of(command.getPortNumber()));

        DiscoPacketSendingConfirmation confirmation = new DiscoPacketSendingConfirmation(
                new NetworkEndpoint(command.getSwitchId(), command.getPortNumber()));
        getKafkaProducer().sendMessageAndTrack(context.getKafkaTopoDiscoTopic(),
                new InfoMessage(confirmation, System.currentTimeMillis(), message.getCorrelationId()));
    }

    private void doPingRequest(CommandContext context, PingRequest request) {
        PingRequestCommand command = new PingRequestCommand(context, request.getPing());
        commandProcessor.process(command);
    }

    private void doDiscoverPathCommand(CommandData data) {
        DiscoverPathCommandData command = (DiscoverPathCommandData) data;
        logger.warn("NOT IMPLEMENTED: sending discover Path to {}", command);
    }

    /**
     * Processes install ingress flow message.
     *
     * @param message command message for flow installation
     */
    private void doProcessIngressFlow(final CommandMessage message, String replyToTopic, Destination replyDestination)
            throws FlowCommandException {
        InstallIngressFlow command = (InstallIngressFlow) message.getData();

        try {
            installIngressFlow(command);
            message.setDestination(replyDestination);
            getKafkaProducer().sendMessageAndTrack(replyToTopic, message);
        } catch (SwitchOperationException e) {
            throw new FlowCommandException(command.getId(), ErrorType.CREATION_FAILURE, e);
        }
    }

    /**
     * Installs ingress flow on the switch.
     *
     * @param command command message for flow installation
     */
    private void installIngressFlow(final InstallIngressFlow command) throws SwitchOperationException {
        logger.debug("Creating an ingress flow: {}", command);

        long meterId = 0;
        if (command.getMeterId() != null && command.getMeterId() > 0) {
            meterId = allocateMeterId(
                    command.getMeterId(), command.getSwitchId(), command.getId(), command.getCookie());

            context.getSwitchManager().installMeter(
                    DatapathId.of(command.getSwitchId().toLong()),
                    command.getBandwidth(), 1024, meterId);
        } else {
            logger.debug("Installing unmetered ingress flow. Switch: {}, cookie: {}",
                    command.getSwitchId(), command.getCookie());
        }

        context.getSwitchManager().installIngressFlow(
                DatapathId.of(command.getSwitchId().toLong()),
                command.getId(),
                command.getCookie(),
                command.getInputPort(),
                command.getOutputPort(),
                command.getInputVlanId(),
                command.getTransitVlanId(),
                command.getOutputVlanType(),
                meterId);
    }

    /**
     * Processes egress flow install message.
     *
     * @param message command message for flow installation
     */
    private void doProcessEgressFlow(final CommandMessage message, String replyToTopic, Destination replyDestination)
            throws FlowCommandException {
        InstallEgressFlow command = (InstallEgressFlow) message.getData();

        try {
            installEgressFlow(command);
            message.setDestination(replyDestination);
            getKafkaProducer().sendMessageAndTrack(replyToTopic, message);
        } catch (SwitchOperationException e) {
            throw new FlowCommandException(command.getId(), ErrorType.CREATION_FAILURE, e);
        }
    }

    /**
     * Installs egress flow on the switch.
     *
     * @param command command message for flow installation
     */
    private void installEgressFlow(InstallEgressFlow command) throws SwitchOperationException {
        logger.debug("Creating an egress flow: {}", command);

        context.getSwitchManager().installEgressFlow(
                DatapathId.of(command.getSwitchId().toLong()),
                command.getId(),
                command.getCookie(),
                command.getInputPort(),
                command.getOutputPort(),
                command.getTransitVlanId(),
                command.getOutputVlanId(),
                command.getOutputVlanType());
    }

    /**
     * Processes transit flow installing message.
     *
     * @param message command message for flow installation
     */
    private void doProcessTransitFlow(final CommandMessage message, String replyToTopic, Destination replyDestination)
            throws FlowCommandException {
        InstallTransitFlow command = (InstallTransitFlow) message.getData();

        try {
            installTransitFlow(command);
            message.setDestination(replyDestination);
            getKafkaProducer().sendMessageAndTrack(replyToTopic, message);
        } catch (SwitchOperationException e) {
            throw new FlowCommandException(command.getId(), ErrorType.CREATION_FAILURE, e);
        }
    }

    /**
     * Installs transit flow on the switch.
     *
     * @param command command message for flow installation
     */
    private void installTransitFlow(final InstallTransitFlow command) throws SwitchOperationException {
        logger.debug("Creating a transit flow: {}", command);

        context.getSwitchManager().installTransitFlow(
                DatapathId.of(command.getSwitchId().toLong()),
                command.getId(),
                command.getCookie(),
                command.getInputPort(),
                command.getOutputPort(),
                command.getTransitVlanId());
    }

    /**
     * Processes one-switch flow installing message.
     *
     * @param message command message for flow installation
     */
    private void doProcessOneSwitchFlow(final CommandMessage message, String replyToTopic, Destination replyDestination)
            throws FlowCommandException {
        InstallOneSwitchFlow command = (InstallOneSwitchFlow) message.getData();
        logger.debug("creating a flow through one switch: {}", command);

        try {
            installOneSwitchFlow(command);
            message.setDestination(replyDestination);
            getKafkaProducer().sendMessageAndTrack(replyToTopic, message);
        } catch (SwitchOperationException e) {
            throw new FlowCommandException(command.getId(), ErrorType.CREATION_FAILURE, e);
        }
    }

    /**
     * Installs flow through one switch.
     *
     * @param command command message for flow installation
     */
    private void installOneSwitchFlow(InstallOneSwitchFlow command) throws SwitchOperationException {
        long meterId = 0;
        if (command.getMeterId() != null && command.getMeterId() > 0) {
            meterId = allocateMeterId(
                    command.getMeterId(), command.getSwitchId(), command.getId(), command.getCookie());

            context.getSwitchManager().installMeter(
                    DatapathId.of(command.getSwitchId().toLong()),
                    command.getBandwidth(), 1024, meterId);
        } else {
            logger.debug("Installing unmetered one switch flow. Switch: {}, cookie: {}",
                    command.getSwitchId(), command.getCookie());
        }

        OutputVlanType directOutputVlanType = command.getOutputVlanType();
        context.getSwitchManager().installOneSwitchFlow(
                DatapathId.of(command.getSwitchId().toLong()),
                command.getId(),
                command.getCookie(),
                command.getInputPort(),
                command.getOutputPort(),
                command.getInputVlanId(),
                command.getOutputVlanId(),
                directOutputVlanType,
                meterId);
    }

    /**
     * Removes flow.
     *
     * @param message command message for flow installation
     */
    private void doDeleteFlow(final CommandMessage message, String replyToTopic, Destination replyDestination)
            throws FlowCommandException {
        RemoveFlow command = (RemoveFlow) message.getData();
        logger.debug("deleting a flow: {}", command);

        DatapathId dpid = DatapathId.of(command.getSwitchId().toLong());
        ISwitchManager switchManager = context.getSwitchManager();
        try {
            logger.info("Deleting flow {} from switch {}", command.getId(), dpid);

            DeleteRulesCriteria criteria = Optional.ofNullable(command.getCriteria())
                    .orElseGet(() -> DeleteRulesCriteria.builder().cookie(command.getCookie()).build());
            List<Long> cookiesOfRemovedRules = switchManager.deleteRulesByCriteria(dpid, criteria);
            if (cookiesOfRemovedRules.isEmpty()) {
                logger.warn("No rules were removed by criteria {} for flow {} from switch {}",
                        criteria, command.getId(), dpid);
            }

            // FIXME(surabujin): QUICK FIX - try to drop meterPool completely
            Long meterId = command.getMeterId();
            if (meterId != null) {
                switchManager.deleteMeter(dpid, meterId);
            }

            message.setDestination(replyDestination);
            getKafkaProducer().sendMessageAndTrack(replyToTopic, message);
        } catch (SwitchOperationException e) {
            throw new FlowCommandException(command.getId(), ErrorType.DELETION_FAILURE, e);
        }
    }

    /**
     * Create network dump for OFELinkBolt.
     *
     * @param message NetworkCommandData
     */
    private void doNetworkDump(final CommandMessage message) throws SwitchOperationException {
        logger.debug("Processing dump switches request");

        SwitchTrackingService switchTracking = context.getModuleContext().getServiceImpl(SwitchTrackingService.class);
        switchTracking.dumpAllSwitches(message.getCorrelationId());
    }

    private void doInstallSwitchRules(final CommandMessage message, String replyToTopic, Destination replyDestination) {
        SwitchRulesInstallRequest request = (SwitchRulesInstallRequest) message.getData();
        logger.debug("Installing rules on '{}' switch: action={}",
                request.getSwitchId(), request.getInstallRulesAction());

        final IKafkaProducerService producerService = getKafkaProducer();

        DatapathId dpid = DatapathId.of(request.getSwitchId().toLong());
        ISwitchManager switchManager = context.getSwitchManager();
        InstallRulesAction installAction = request.getInstallRulesAction();
        List<Long> installedRules = new ArrayList<>();
        try {
            if (installAction == InstallRulesAction.INSTALL_DROP) {
                switchManager.installDropFlow(dpid);
                installedRules.add(ISwitchManager.DROP_RULE_COOKIE);
            } else if (installAction == InstallRulesAction.INSTALL_BROADCAST) {
                switchManager.installVerificationRule(dpid, true);
                installedRules.add(ISwitchManager.VERIFICATION_BROADCAST_RULE_COOKIE);
            } else if (installAction == InstallRulesAction.INSTALL_UNICAST) {
                // TODO: this isn't always added (ie if OF1.2). Is there a better response?
                switchManager.installVerificationRule(dpid, false);
                installedRules.add(ISwitchManager.VERIFICATION_UNICAST_RULE_COOKIE);
            } else {
                switchManager.installDefaultRules(dpid);
                installedRules.addAll(asList(
                        ISwitchManager.DROP_RULE_COOKIE,
                        ISwitchManager.VERIFICATION_BROADCAST_RULE_COOKIE,
                        ISwitchManager.VERIFICATION_UNICAST_RULE_COOKIE
                ));
            }

            SwitchRulesResponse response = new SwitchRulesResponse(installedRules);
            InfoMessage infoMessage = new InfoMessage(response,
                    System.currentTimeMillis(), message.getCorrelationId(), replyDestination);
            producerService.sendMessageAndTrack(replyToTopic, infoMessage);

        } catch (SwitchOperationException e) {
            ErrorData errorData = new ErrorData(ErrorType.CREATION_FAILURE, e.getMessage(),
                    request.getSwitchId().toString());
            ErrorMessage error = new ErrorMessage(errorData,
                    System.currentTimeMillis(), message.getCorrelationId(), replyDestination);
            producerService.sendMessageAndTrack(replyToTopic, error);
        }
    }

    private void doDeleteSwitchRules(final CommandMessage message, String replyToTopic, Destination replyDestination) {
        SwitchRulesDeleteRequest request = (SwitchRulesDeleteRequest) message.getData();
        logger.debug("Deleting rules from '{}' switch: action={}, criteria={}", request.getSwitchId(),
                request.getDeleteRulesAction(), request.getCriteria());

        final IKafkaProducerService producerService = getKafkaProducer();

        DatapathId dpid = DatapathId.of(request.getSwitchId().toLong());
        DeleteRulesAction deleteAction = request.getDeleteRulesAction();
        DeleteRulesCriteria criteria = request.getCriteria();

        ISwitchManager switchManager = context.getSwitchManager();

        try {
            List<Long> removedRules = new ArrayList<>();

            if (deleteAction != null) {
                switch (deleteAction) {
                    case REMOVE_DROP:
                        criteria = DeleteRulesCriteria.builder()
                                .cookie(ISwitchManager.DROP_RULE_COOKIE).build();
                        break;
                    case REMOVE_BROADCAST:
                        criteria = DeleteRulesCriteria.builder()
                                .cookie(ISwitchManager.VERIFICATION_BROADCAST_RULE_COOKIE).build();
                        break;
                    case REMOVE_UNICAST:
                        criteria = DeleteRulesCriteria.builder()
                                .cookie(ISwitchManager.VERIFICATION_UNICAST_RULE_COOKIE).build();
                        break;
                    case REMOVE_VERIFICATION_LOOP:
                        criteria = DeleteRulesCriteria.builder()
                                .cookie(ISwitchManager.DROP_VERIFICATION_LOOP_RULE_COOKIE).build();
                        break;
                    default:
                        logger.warn("Received unexpected delete switch rule action: {}", deleteAction);
                }

                // The cases when we delete all non-default rules.
                if (deleteAction.nonDefaultRulesToBeRemoved()) {
                    removedRules.addAll(switchManager.deleteAllNonDefaultRules(dpid));
                }

                // The cases when we delete the default rules.
                if (deleteAction.defaultRulesToBeRemoved()) {
                    removedRules.addAll(switchManager.deleteDefaultRules(dpid));
                }
            }

            // The case when we either delete by criteria or a specific default rule.
            if (criteria != null) {
                removedRules.addAll(switchManager.deleteRulesByCriteria(dpid, criteria));
            }

            // The cases when we (re)install the default rules.
            if (deleteAction != null && deleteAction.defaultRulesToBeInstalled()) {
                switchManager.installDefaultRules(dpid);
            }

            SwitchRulesResponse response = new SwitchRulesResponse(removedRules);
            InfoMessage infoMessage = new InfoMessage(response,
                    System.currentTimeMillis(), message.getCorrelationId(), replyDestination);
            producerService.sendMessageAndTrack(replyToTopic, infoMessage);

        } catch (SwitchNotFoundException e) {
            logger.info("Deleting switch rules is unsuccessful. Switch {} not found", request.getSwitchId());
            ErrorData errorData = new ErrorData(ErrorType.NOT_FOUND, e.getMessage(),
                    request.getSwitchId().toString());
            ErrorMessage error = new ErrorMessage(errorData,
                    System.currentTimeMillis(), message.getCorrelationId(), replyDestination);
            producerService.sendMessageAndTrack(replyToTopic, error);
        } catch (SwitchOperationException e) {
            ErrorData errorData = new ErrorData(ErrorType.DELETION_FAILURE, e.getMessage(),
                    request.getSwitchId().toString());
            ErrorMessage error = new ErrorMessage(errorData,
                    System.currentTimeMillis(), message.getCorrelationId(), replyDestination);
            producerService.sendMessageAndTrack(replyToTopic, error);
        }
    }

    private void doConnectMode(final CommandMessage message, String replyToTopic, Destination replyDestination) {
        ConnectModeRequest request = (ConnectModeRequest) message.getData();
        if (request.getMode() != null) {
            logger.debug("Setting CONNECT MODE to '{}'", request.getMode());
        } else {
            logger.debug("Getting CONNECT MODE");
        }

        ISwitchManager switchManager = context.getSwitchManager();
        ConnectModeRequest.Mode result = switchManager.connectMode(request.getMode());

        logger.debug("CONNECT MODE is now '{}'", result);
        ConnectModeResponse response = new ConnectModeResponse(result);
        InfoMessage infoMessage = new InfoMessage(response,
                System.currentTimeMillis(), message.getCorrelationId(), replyDestination);
        getKafkaProducer().sendMessageAndTrack(replyToTopic, infoMessage);

    }

    private void doDumpRulesRequest(final CommandMessage message,  String replyToTopic, Destination replyDestination) {
        DumpRulesRequest request = (DumpRulesRequest) message.getData();

        final IKafkaProducerService producerService = getKafkaProducer();

        try {
            SwitchId switchId = request.getSwitchId();
            logger.debug("Loading installed rules for switch {}", switchId);

            List<OFFlowStatsEntry> flowEntries =
                    context.getSwitchManager().dumpFlowTable(DatapathId.of(switchId.toLong()));
            List<FlowEntry> flows = flowEntries.stream()
                    .map(OfFlowStatsConverter::toFlowEntry)
                    .collect(Collectors.toList());

            SwitchFlowEntries response = SwitchFlowEntries.builder()
                    .switchId(switchId)
                    .flowEntries(flows)
                    .build();
            InfoMessage infoMessage = new InfoMessage(response, message.getTimestamp(),
                    message.getCorrelationId());
            producerService.sendMessageAndTrack(replyToTopic, infoMessage);
        } catch (SwitchOperationException e) {
            logger.info("Dump rules is unsuccessful. Switch {} not found", request.getSwitchId());
            ErrorData errorData = new DumpRulesErrorData(ErrorType.NOT_FOUND, e.getMessage(),
                    "The switch was not found when requesting a rules dump.");
            ErrorMessage error = new ErrorMessage(errorData,
                    System.currentTimeMillis(), message.getCorrelationId(), replyDestination);
            producerService.sendMessageAndTrack(replyToTopic, error);
        }
    }

    /**
     * Batch install of flows on the switch.
     *
     * @param message with list of flows.
     */
    private void doBatchInstall(final CommandMessage message) throws FlowCommandException {
        BatchInstallRequest request = (BatchInstallRequest) message.getData();
        final String switchId = request.getSwitchId();
        logger.debug("Processing flow commands for switch {}", switchId);

        for (BaseInstallFlow command : request.getFlowCommands()) {
            logger.debug("Processing command for switch {} {}", switchId, command);
            try {
                if (command instanceof InstallIngressFlow) {
                    installIngressFlow((InstallIngressFlow) command);
                } else if (command instanceof InstallEgressFlow) {
                    installEgressFlow((InstallEgressFlow) command);
                } else if (command instanceof InstallTransitFlow) {
                    installTransitFlow((InstallTransitFlow) command);
                } else if (command instanceof InstallOneSwitchFlow) {
                    installOneSwitchFlow((InstallOneSwitchFlow) command);
                } else {
                    throw new FlowCommandException(command.getId(), ErrorType.REQUEST_INVALID,
                            "Unsupported command for batch install.");
                }
            } catch (SwitchOperationException e) {
                logger.error("Error during flow installation", e);
            }
        }
    }

    private void doPortsCommandDataRequest(CommandMessage message) {
        ISwitchManager switchManager = context.getModuleContext().getServiceImpl(ISwitchManager.class);

        try {
            PortsCommandData request = (PortsCommandData) message.getData();
            Map<DatapathId, IOFSwitch> allSwitchMap = context.getSwitchManager().getAllSwitchMap();
            for (Map.Entry<DatapathId, IOFSwitch> entry : allSwitchMap.entrySet()) {
                SwitchId switchId = new SwitchId(entry.getKey().toString());
                try {
                    IOFSwitch sw = entry.getValue();

                    Set<PortStatus> statuses = new HashSet<>();
                    for (OFPortDesc portDesc : switchManager.getPhysicalPorts(sw.getId())) {
                        statuses.add(new PortStatus(portDesc.getPortNo().getPortNumber(),
                                                    portDesc.isEnabled() ? PortChangeType.UP : PortChangeType.DOWN));
                    }

                    SwitchPortStatusData response = SwitchPortStatusData.builder()
                            .switchId(switchId)
                            .ports(statuses)
                            .requester(request.getRequester())
                            .build();

                    InfoMessage infoMessage = new InfoMessage(response, message.getTimestamp(),
                            message.getCorrelationId());
                    getKafkaProducer().sendMessageAndTrack(context.getKafkaStatsTopic(), infoMessage);
                } catch (Exception e) {
                    logger.error("Could not get port stats data for switch {} with error {}",
                            switchId, e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            logger.error("Could not get port data for stats {}", e.getMessage(), e);
        }
    }

    private void doDeleteMeter(CommandMessage message, String replyToTopic, Destination replyDestination) {
        DeleteMeterRequest request = (DeleteMeterRequest) message.getData();

        final IKafkaProducerService producerService = getKafkaProducer();

        try {
            DatapathId dpid = DatapathId.of(request.getSwitchId().toLong());
            long txId = context.getSwitchManager().deleteMeter(dpid, request.getMeterId());

            DeleteMeterResponse response = new DeleteMeterResponse(txId != 0L);
            InfoMessage infoMessage = new InfoMessage(response, System.currentTimeMillis(), message.getCorrelationId(),
                    replyDestination);
            producerService.sendMessageAndTrack(replyToTopic, infoMessage);
        } catch (SwitchOperationException e) {
            logger.info("Meter deletion is unsuccessful. Switch {} not found", request.getSwitchId());
            ErrorData errorData =
                    new ErrorData(ErrorType.DATA_INVALID, e.getMessage(), request.getSwitchId().toString());
            ErrorMessage error = new ErrorMessage(errorData,
                    System.currentTimeMillis(), message.getCorrelationId(), replyDestination);
            producerService.sendMessageAndTrack(replyToTopic, error);
        }
    }

    private void doConfigurePort(final CommandMessage message, final String replyToTopic, 
            final Destination replyDestination) {
        PortConfigurationRequest request = (PortConfigurationRequest) message.getData();
        
        logger.info("Port configuration request. Switch '{}', Port '{}'", request.getSwitchId(), 
                request.getPortNumber());

        final IKafkaProducerService producerService = getKafkaProducer();

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
            ErrorData errorData = new ErrorData(ErrorType.DATA_INVALID, e.getMessage(), 
                    "Port configuration request failed");
            ErrorMessage error = new ErrorMessage(errorData,
                    System.currentTimeMillis(), message.getCorrelationId(), replyDestination);
            producerService.sendMessageAndTrack(replyToTopic, error);
        }
    }

    private void doDumpSwitchPortsDescriptionRequest(
            CommandMessage message, String replyToTopic, Destination replyDestination) {
        DumpSwitchPortsDescriptionRequest request = (DumpSwitchPortsDescriptionRequest) message.getData();

        final IKafkaProducerService producerService = getKafkaProducer();

        try {
            SwitchId switchId = request.getSwitchId();
            logger.debug("Get port descriptions for switch {}", switchId);

            SwitchPortsDescription response = getSwitchPortsDescription(switchId);

            InfoMessage infoMessage = new InfoMessage(response, message.getTimestamp(), message.getCorrelationId());
            producerService.sendMessageAndTrack(replyToTopic, infoMessage);
        } catch (SwitchOperationException e) {
            logger.error("Unable to dump switch port descriptions request", e);
            ErrorData errorData =
                    new ErrorData(
                            ErrorType.NOT_FOUND, e.getMessage(), "Unable to dump switch port descriptions request");
            ErrorMessage error =
                    new ErrorMessage(
                            errorData, System.currentTimeMillis(), message.getCorrelationId(), replyDestination);
            producerService.sendMessageAndTrack(replyToTopic, error);
        }
    }

    private SwitchPortsDescription getSwitchPortsDescription(SwitchId switchId) throws SwitchOperationException {

        List<OFPortDesc> ofPortsDescriptions =
                context.getSwitchManager().dumpPortsDescription(DatapathId.of(switchId.toLong()));
        List<PortDescription> portsDescriptions = ofPortsDescriptions.stream()
                .map(OfPortDescConverter::toPortDescription)
                .collect(Collectors.toList());

        return SwitchPortsDescription.builder()
                .version(ofPortsDescriptions.get(0).getVersion().toString())
                .portsDescription(portsDescriptions)
                .build();
    }

    private void doDumpPortDescriptionRequest(
            CommandMessage message, String replyToTopic, Destination replyDestination) {
        DumpPortDescriptionRequest request = (DumpPortDescriptionRequest) message.getData();

        final IKafkaProducerService producerService = getKafkaProducer();

        try {
            SwitchId switchId = request.getSwitchId();
            logger.debug("Get port description for switch {}", switchId);
            SwitchPortsDescription switchPortsDescription = getSwitchPortsDescription(switchId);

            int port = request.getPortNumber();
            PortDescription response = switchPortsDescription.getPortsDescription()
                    .stream()
                    .filter(x -> x.getPortNumber() == port)
                    .findFirst()
                    .orElseThrow(() -> new SwitchOperationException(
                            DatapathId.of(switchId.toLong()),
                            String.format("Port %d does not exist on the switch %s", port, switchId)));

            InfoMessage infoMessage = new InfoMessage(response, message.getTimestamp(), message.getCorrelationId());
            producerService.sendMessageAndTrack(replyToTopic, infoMessage);
        } catch (SwitchOperationException e) {
            logger.error("Unable to dump port description request", e);
            ErrorData errorData =
                    new ErrorData(ErrorType.NOT_FOUND, e.getMessage(), "Unable to dump port description request");
            ErrorMessage error =
                    new ErrorMessage(
                            errorData, System.currentTimeMillis(), message.getCorrelationId(), replyDestination);
            producerService.sendMessageAndTrack(replyToTopic, error);
        }
    }

    private long allocateMeterId(Long meterId, SwitchId switchId, String flowId, Long cookie) {
        long allocatedId;

        if (meterId == null) {
            logger.error("Meter_id should be passed within one switch flow command. Cookie is {}", cookie);
            allocatedId = (long) meterPool.allocate(switchId, flowId);
            logger.error("Allocated meter_id {} for cookie {}", allocatedId, cookie);
        } else {
            allocatedId = meterPool.allocate(switchId, flowId, Math.toIntExact(meterId));
        }
        return allocatedId;
    }

    private void parseRecord(ConsumerRecord<String, String> record) {
        CommandMessage message;
        try {
            String value = record.value();
            // TODO: Prior to Message changes, this MAPPER would read Message ..
            //          but, changed to BaseMessage and got an error wrt "timestamp" ..
            //          so, need to experiment with why CommandMessage can't be read as
            //          a BaseMessage
            message = MAPPER.readValue(value, CommandMessage.class);
        } catch (Exception exception) {
            logger.error("error parsing record={}", record.value(), exception);
            return;
        }

        // Process the message within the message correlation context.
        try (CorrelationContextClosable closable = CorrelationContext.create(message.getCorrelationId())) {
            doControllerMsg(message);
        } catch (Exception exception) {
            logger.error("error processing message={}", message, exception);
        }
    }

    @Override
    public void run() {
        parseRecord(record);
    }

    private IKafkaProducerService getKafkaProducer() {
        return context.getModuleContext().getServiceImpl(IKafkaProducerService.class);
    }

    public static class Factory {
        private final ConsumerContext context;
        private final MeterPool meterPool = new MeterPool();

        public Factory(ConsumerContext context) {
            this.context = context;
        }

        public RecordHandler produce(ConsumerRecord<String, String> record) {
            return new RecordHandler(context, record, meterPool);
        }
    }
}
