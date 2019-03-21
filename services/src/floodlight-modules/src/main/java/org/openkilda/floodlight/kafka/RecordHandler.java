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

package org.openkilda.floodlight.kafka;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static org.openkilda.floodlight.kafka.ErrorMessageBuilder.anError;
import static org.openkilda.messaging.Utils.MAPPER;
import static org.openkilda.model.Cookie.CATCH_BFD_RULE_COOKIE;
import static org.openkilda.model.Cookie.DROP_RULE_COOKIE;
import static org.openkilda.model.Cookie.DROP_VERIFICATION_LOOP_RULE_COOKIE;
import static org.openkilda.model.Cookie.VERIFICATION_BROADCAST_RULE_COOKIE;
import static org.openkilda.model.Cookie.VERIFICATION_UNICAST_RULE_COOKIE;

import org.openkilda.floodlight.command.Command;
import org.openkilda.floodlight.command.CommandContext;
import org.openkilda.floodlight.converter.OfFlowStatsMapper;
import org.openkilda.floodlight.converter.OfMeterConverter;
import org.openkilda.floodlight.converter.OfPortDescConverter;
import org.openkilda.floodlight.error.FlowCommandException;
import org.openkilda.floodlight.error.SwitchNotFoundException;
import org.openkilda.floodlight.error.SwitchOperationException;
import org.openkilda.floodlight.error.UnsupportedSwitchOperationException;
import org.openkilda.floodlight.kafka.dispatcher.CommandDispatcher;
import org.openkilda.floodlight.kafka.dispatcher.PingRequestDispatcher;
import org.openkilda.floodlight.kafka.dispatcher.RemoveBfdSessionDispatcher;
import org.openkilda.floodlight.kafka.dispatcher.SetupBfdSessionDispatcher;
import org.openkilda.floodlight.service.CommandProcessorService;
import org.openkilda.floodlight.service.kafka.IKafkaProducerService;
import org.openkilda.floodlight.switchmanager.ISwitchManager;
import org.openkilda.floodlight.switchmanager.SwitchTrackingService;
import org.openkilda.floodlight.utils.CorrelationContext;
import org.openkilda.floodlight.utils.CorrelationContext.CorrelationContextClosable;
import org.openkilda.messaging.AliveRequest;
import org.openkilda.messaging.AliveResponse;
import org.openkilda.messaging.Destination;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.discovery.DiscoverIslCommandData;
import org.openkilda.messaging.command.discovery.DiscoverPathCommandData;
import org.openkilda.messaging.command.discovery.NetworkCommandData;
import org.openkilda.messaging.command.discovery.PortsCommandData;
import org.openkilda.messaging.command.flow.BaseInstallFlow;
import org.openkilda.messaging.command.flow.BatchInstallForSwitchManagerRequest;
import org.openkilda.messaging.command.flow.BatchInstallRequest;
import org.openkilda.messaging.command.flow.DeleteMeterRequest;
import org.openkilda.messaging.command.flow.InstallEgressFlow;
import org.openkilda.messaging.command.flow.InstallIngressFlow;
import org.openkilda.messaging.command.flow.InstallOneSwitchFlow;
import org.openkilda.messaging.command.flow.InstallTransitFlow;
import org.openkilda.messaging.command.flow.MeterModifyCommandRequest;
import org.openkilda.messaging.command.flow.RemoveFlow;
import org.openkilda.messaging.command.switches.ConnectModeRequest;
import org.openkilda.messaging.command.switches.DeleteRulesAction;
import org.openkilda.messaging.command.switches.DeleteRulesCriteria;
import org.openkilda.messaging.command.switches.DumpMetersForSwitchManagerRequest;
import org.openkilda.messaging.command.switches.DumpMetersRequest;
import org.openkilda.messaging.command.switches.DumpPortDescriptionRequest;
import org.openkilda.messaging.command.switches.DumpRulesForSwitchManagerRequest;
import org.openkilda.messaging.command.switches.DumpRulesRequest;
import org.openkilda.messaging.command.switches.DumpSwitchPortsDescriptionRequest;
import org.openkilda.messaging.command.switches.InstallRulesAction;
import org.openkilda.messaging.command.switches.PortConfigurationRequest;
import org.openkilda.messaging.command.switches.SwitchRulesDeleteRequest;
import org.openkilda.messaging.command.switches.SwitchRulesInstallRequest;
import org.openkilda.messaging.command.switches.ValidateRulesRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.error.rule.FlowCommandErrorData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.discovery.DiscoPacketSendingConfirmation;
import org.openkilda.messaging.info.meter.FlowMeterEntries;
import org.openkilda.messaging.info.meter.MeterEntry;
import org.openkilda.messaging.info.meter.SwitchMeterEntries;
import org.openkilda.messaging.info.rule.BatchInstallResponse;
import org.openkilda.messaging.info.rule.FlowEntry;
import org.openkilda.messaging.info.rule.SwitchFlowEntries;
import org.openkilda.messaging.info.stats.PortStatusData;
import org.openkilda.messaging.info.stats.SwitchPortStatusData;
import org.openkilda.messaging.info.switches.ConnectModeResponse;
import org.openkilda.messaging.info.switches.DeleteMeterResponse;
import org.openkilda.messaging.info.switches.PortConfigurationResponse;
import org.openkilda.messaging.info.switches.PortDescription;
import org.openkilda.messaging.info.switches.SwitchPortsDescription;
import org.openkilda.messaging.info.switches.SwitchRulesResponse;
import org.openkilda.messaging.model.NetworkEndpoint;
import org.openkilda.model.OutputVlanType;
import org.openkilda.model.PortStatus;
import org.openkilda.model.SwitchId;

import com.google.common.collect.ImmutableList;
import net.floodlightcontroller.core.IOFSwitch;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.projectfloodlight.openflow.protocol.OFFlowStatsEntry;
import org.projectfloodlight.openflow.protocol.OFMeterConfig;
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
    private final List<CommandDispatcher<?>> dispatchers;
    private final ConsumerRecord<String, String> record;

    private final CommandProcessorService commandProcessor;

    public RecordHandler(ConsumerContext context, List<CommandDispatcher<?>> dispatchers,
                         ConsumerRecord<String, String> record) {
        this.context = context;
        this.dispatchers = dispatchers;
        this.record = record;

        this.commandProcessor = context.getModuleContext().getServiceImpl(CommandProcessorService.class);
    }

    protected void doControllerMsg(CommandMessage message) {
        // Define the destination topic where the reply will be sent to.
        final String replyToTopic = context.getKafkaFlowTopic();
        final Destination replyDestination = getDestinationForTopic(replyToTopic);

        try {
            handleCommand(message, replyToTopic, replyDestination);
        } catch (SwitchOperationException e) {
            logger.error("Unable to handle request {}: {}", message.getData().getClass().getName(), e.getMessage());
        } catch (FlowCommandException e) {
            String errorMessage = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            logger.error("Failed to handle message {}: {}", message, errorMessage);
            ErrorData errorData = new FlowCommandErrorData(e.getFlowId(), e.getCookie(), e.getTransactionId(),
                    e.getErrorType(), errorMessage, e.getMessage());
            ErrorMessage error = new ErrorMessage(errorData, System.currentTimeMillis(),
                    message.getCorrelationId(), replyDestination);
            getKafkaProducer().sendMessageAndTrack(replyToTopic, error);
        }
    }

    private void handleCommand(CommandMessage message, String replyToTopic, Destination replyDestination)
            throws FlowCommandException, SwitchOperationException {
        logger.debug("Handling message: '{}'. Reply topic: '{}'. Reply destination: '{}'.",
                message, replyToTopic, replyDestination);
        CommandData data = message.getData();

        if (data instanceof DiscoverIslCommandData) {
            doDiscoverIslCommand(message);
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
            doDeleteSwitchRules(message);
        } else if (data instanceof SwitchRulesInstallRequest) {
            doInstallSwitchRules(message);
        } else if (data instanceof ConnectModeRequest) {
            doConnectMode(message);
        } else if (data instanceof ValidateRulesRequest) {
            doValidateRulesRequest(message);
        } else if (data instanceof DumpRulesRequest) {
            doDumpRulesRequest(message);
        } else if (data instanceof DumpRulesForSwitchManagerRequest) {
            doDumpRulesForSwitchManagerRequest(message);
        } else if (data instanceof BatchInstallRequest) {
            doBatchInstall(message);
        } else if (data instanceof BatchInstallForSwitchManagerRequest) {
            doBatchInstallForSwitchManager(message);
        } else if (data instanceof PortsCommandData) {
            doPortsCommandDataRequest(message);
        } else if (data instanceof DeleteMeterRequest) {
            doDeleteMeter(message);
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
        } else if (data instanceof MeterModifyCommandRequest) {
            doModifyMeterRequest(message);
        } else if (data instanceof AliveRequest) {
            doAliveRequest(message);
        } else {
            logger.error("Unable to handle '{}' request - handler not found.", data);
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

    private void doAliveRequest(CommandMessage message) {
        // TODO(tdurakov): return logic for failed amount counter
        int totalFailedAmount = getKafkaProducer().getFailedSendMessageCounter();
        getKafkaProducer().sendMessageAndTrack(context.getKafkaTopoDiscoTopic(),
                new InfoMessage(new AliveResponse(context.getRegion(), totalFailedAmount), System.currentTimeMillis(),
                        message.getCorrelationId(), context.getRegion()));
    }

    private void doDiscoverIslCommand(CommandMessage message) {
        DiscoverIslCommandData command = (DiscoverIslCommandData) message.getData();
        SwitchId switchId = command.getSwitchId();
        context.getPathVerificationService().sendDiscoveryMessage(
                DatapathId.of(switchId.toLong()), OFPort.of(command.getPortNumber()), command.getPacketId());

        DiscoPacketSendingConfirmation confirmation = new DiscoPacketSendingConfirmation(
                new NetworkEndpoint(command.getSwitchId(), command.getPortNumber()), command.getPacketId());
        getKafkaProducer().sendMessageAndTrack(context.getKafkaTopoDiscoTopic(), command.getSwitchId().toString(),
                new InfoMessage(confirmation, System.currentTimeMillis(), message.getCorrelationId(),
                        context.getRegion()));
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
        logger.info("Installing ingress flow '{}' on switch '{}'", command.getId(), command.getSwitchId());

        try {
            installIngressFlow(command);
            message.setDestination(replyDestination);
            getKafkaProducer().sendMessageAndTrack(replyToTopic, message);
        } catch (SwitchOperationException e) {
            throw new FlowCommandException(command.getId(), command.getCookie(), command.getTransactionId(),
                    ErrorType.CREATION_FAILURE, e);
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
            meterId = command.getMeterId();

            installMeter(DatapathId.of(command.getSwitchId().toLong()), meterId, command.getBandwidth(),
                    command.getId());
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
        logger.info("Installing egress flow '{}' on switch '{}'", command.getId(), command.getSwitchId());

        try {
            installEgressFlow(command);
            message.setDestination(replyDestination);
            getKafkaProducer().sendMessageAndTrack(replyToTopic, message);
        } catch (SwitchOperationException e) {
            throw new FlowCommandException(command.getId(), command.getCookie(), command.getTransactionId(),
                    ErrorType.CREATION_FAILURE, e);
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
        logger.info("Installing transit flow '{}' on switch '{}'", command.getId(), command.getSwitchId());

        try {
            installTransitFlow(command);
            message.setDestination(replyDestination);
            getKafkaProducer().sendMessageAndTrack(replyToTopic, message);
        } catch (SwitchOperationException e) {
            throw new FlowCommandException(command.getId(), command.getCookie(), command.getTransactionId(),
                    ErrorType.CREATION_FAILURE, e);
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
        logger.info("Installing one switch flow '{}' on switch '{}'", command.getId(), command.getSwitchId());

        try {
            installOneSwitchFlow(command);
            message.setDestination(replyDestination);
            getKafkaProducer().sendMessageAndTrack(replyToTopic, message);
        } catch (SwitchOperationException e) {
            throw new FlowCommandException(command.getId(), command.getCookie(), command.getTransactionId(),
                    ErrorType.CREATION_FAILURE, e);
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
            meterId = command.getMeterId();
            installMeter(DatapathId.of(command.getSwitchId().toLong()), meterId, command.getBandwidth(),
                    command.getId());
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
        } catch (SwitchOperationException e) {
            throw new FlowCommandException(command.getId(), command.getCookie(), command.getTransactionId(),
                    ErrorType.DELETION_FAILURE, e);
        }

        // FIXME(surabujin): QUICK FIX - try to drop meterPool completely
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

        message.setDestination(replyDestination);
        getKafkaProducer().sendMessageAndTrack(replyToTopic, message);
    }

    /**
     * Create network dump for OFELinkBolt.
     *
     * @param message NetworkCommandData
     */
    private void doNetworkDump(final CommandMessage message) throws SwitchOperationException {
        logger.info("Processing request from WFM to dump switches. {}", message.getCorrelationId());

        SwitchTrackingService switchTracking = context.getModuleContext().getServiceImpl(SwitchTrackingService.class);
        switchTracking.dumpAllSwitches(message.getCorrelationId());
    }

    private void doInstallSwitchRules(final CommandMessage message) {
        SwitchRulesInstallRequest request = (SwitchRulesInstallRequest) message.getData();
        logger.info("Installing rules on '{}' switch: action={}",
                request.getSwitchId(), request.getInstallRulesAction());

        final IKafkaProducerService producerService = getKafkaProducer();
        final String replyToTopic = context.getKafkaNorthboundTopic();

        DatapathId dpid = DatapathId.of(request.getSwitchId().toLong());
        ISwitchManager switchManager = context.getSwitchManager();
        InstallRulesAction installAction = request.getInstallRulesAction();
        List<Long> installedRules = new ArrayList<>();
        try {
            if (installAction == InstallRulesAction.INSTALL_DROP) {
                switchManager.installDropFlow(dpid);
                installedRules.add(DROP_RULE_COOKIE);
            } else if (installAction == InstallRulesAction.INSTALL_BROADCAST) {
                switchManager.installVerificationRule(dpid, true);
                installedRules.add(VERIFICATION_BROADCAST_RULE_COOKIE);
            } else if (installAction == InstallRulesAction.INSTALL_UNICAST) {
                // TODO: this isn't always added (ie if OF1.2). Is there a better response?
                switchManager.installVerificationRule(dpid, false);
                installedRules.add(VERIFICATION_UNICAST_RULE_COOKIE);
            } else if (installAction == InstallRulesAction.INSTALL_BFD_CATCH) {
                // TODO: this isn't installed as well. Refactor this section
                switchManager.installBfdCatchFlow(dpid);
                installedRules.add(CATCH_BFD_RULE_COOKIE);
            } else {
                switchManager.installDefaultRules(dpid);
                installedRules.addAll(asList(
                        DROP_RULE_COOKIE,
                        VERIFICATION_BROADCAST_RULE_COOKIE,
                        VERIFICATION_UNICAST_RULE_COOKIE,
                        DROP_VERIFICATION_LOOP_RULE_COOKIE,
                        CATCH_BFD_RULE_COOKIE
                ));
            }

            SwitchRulesResponse response = new SwitchRulesResponse(installedRules);
            InfoMessage infoMessage = new InfoMessage(response,
                    System.currentTimeMillis(), message.getCorrelationId());
            producerService.sendMessageAndTrack(replyToTopic, infoMessage);

        } catch (SwitchOperationException e) {
            logger.error("Failed to install rules on switch '{}'", request.getSwitchId(), e);
            anError(ErrorType.CREATION_FAILURE)
                    .withMessage(e.getMessage())
                    .withDescription(request.getSwitchId().toString())
                    .withCorrelationId(message.getCorrelationId())
                    .withTopic(replyToTopic)
                    .sendVia(producerService);
        }
    }

    private void doDeleteSwitchRules(final CommandMessage message) {
        SwitchRulesDeleteRequest request = (SwitchRulesDeleteRequest) message.getData();
        logger.info("Deleting rules from '{}' switch: action={}, criteria={}", request.getSwitchId(),
                request.getDeleteRulesAction(), request.getCriteria());

        final IKafkaProducerService producerService = getKafkaProducer();
        final String replyToTopic = context.getKafkaNorthboundTopic();

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
                                .cookie(DROP_RULE_COOKIE).build();
                        break;
                    case REMOVE_BROADCAST:
                        criteria = DeleteRulesCriteria.builder()
                                .cookie(VERIFICATION_BROADCAST_RULE_COOKIE).build();
                        break;
                    case REMOVE_UNICAST:
                        criteria = DeleteRulesCriteria.builder()
                                .cookie(VERIFICATION_UNICAST_RULE_COOKIE).build();
                        break;
                    case REMOVE_VERIFICATION_LOOP:
                        criteria = DeleteRulesCriteria.builder()
                                .cookie(DROP_VERIFICATION_LOOP_RULE_COOKIE).build();
                        break;
                    case REMOVE_BFD_CATCH:
                        criteria = DeleteRulesCriteria.builder()
                                .cookie(CATCH_BFD_RULE_COOKIE).build();
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
                    System.currentTimeMillis(), message.getCorrelationId());
            producerService.sendMessageAndTrack(replyToTopic, infoMessage);

        } catch (SwitchNotFoundException e) {
            logger.error("Deleting switch rules was unsuccessful. Switch '{}' not found", request.getSwitchId());
            anError(ErrorType.NOT_FOUND)
                    .withMessage(e.getMessage())
                    .withDescription(request.getSwitchId().toString())
                    .withCorrelationId(message.getCorrelationId())
                    .withTopic(replyToTopic)
                    .sendVia(producerService);
        } catch (SwitchOperationException e) {
            logger.error("Failed to delete switch '{}' rules.", request.getSwitchId(), e);
            anError(ErrorType.DELETION_FAILURE)
                    .withMessage(e.getMessage())
                    .withDescription(request.getSwitchId().toString())
                    .withCorrelationId(message.getCorrelationId())
                    .withTopic(replyToTopic)
                    .sendVia(producerService);
        }
    }

    private void doConnectMode(final CommandMessage message) {
        ConnectModeRequest request = (ConnectModeRequest) message.getData();
        if (request.getMode() != null) {
            logger.debug("Setting CONNECT MODE to '{}'", request.getMode());
        } else {
            logger.debug("Getting CONNECT MODE");
        }

        ISwitchManager switchManager = context.getSwitchManager();
        ConnectModeRequest.Mode result = switchManager.connectMode(request.getMode());

        logger.info("CONNECT MODE is now '{}'", result);
        ConnectModeResponse response = new ConnectModeResponse(result);
        InfoMessage infoMessage = new InfoMessage(response,
                System.currentTimeMillis(), message.getCorrelationId());
        getKafkaProducer().sendMessageAndTrack(context.getKafkaNorthboundTopic(), infoMessage);

    }

    private void doDumpRulesRequest(final CommandMessage message) {
        processDumpRulesRequest(((DumpRulesRequest) message.getData()).getSwitchId(),
                context.getKafkaNorthboundTopic(), message.getCorrelationId(), message.getTimestamp());
    }

    private void doDumpRulesForSwitchManagerRequest(final CommandMessage message) {
        processDumpRulesRequest(((DumpRulesForSwitchManagerRequest) message.getData()).getSwitchId(),
                context.getKafkaSwitchManagerTopic(), message.getCorrelationId(), message.getTimestamp());
    }

    private void doValidateRulesRequest(final CommandMessage message) {
        processDumpRulesRequest(((ValidateRulesRequest) message.getData()).getSwitchId(),
                context.getKafkaNbWorkerTopic(), message.getCorrelationId(), message.getTimestamp());
    }

    private void processDumpRulesRequest(final SwitchId switchId, final String replyToTopic,
                                         String correlationId, long timestamp) {
        final IKafkaProducerService producerService = getKafkaProducer();

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
            InfoMessage infoMessage = new InfoMessage(response, timestamp, correlationId);
            producerService.sendMessageAndTrack(replyToTopic, correlationId, infoMessage);
        } catch (SwitchOperationException e) {
            logger.error("Dumping of rules on switch '{}' was unsuccessful: {}", switchId, e.getMessage());
            anError(ErrorType.NOT_FOUND)
                    .withMessage(e.getMessage())
                    .withDescription("The switch was not found when requesting a rules dump.")
                    .withCorrelationId(correlationId)
                    .withTopic(replyToTopic)
                    .sendVia(producerService);
        }
    }

    /**
     * Batch install of flows on the switch.
     *
     * @param message with list of flows.
     */
    private void doBatchInstall(final CommandMessage message) throws FlowCommandException {
        BatchInstallRequest request = (BatchInstallRequest) message.getData();
        try {
            installFlow(request.getSwitchId(), request.getFlowCommands());
        } catch (SwitchOperationException e) {
            logger.error("Error during flow installation", e);
        }
    }

    /**
     * Batch install of flows on the switch from SwitchManager topology.
     *
     * @param message with list of flows.
     */
    private void doBatchInstallForSwitchManager(final CommandMessage message) {
        BatchInstallForSwitchManagerRequest request = (BatchInstallForSwitchManagerRequest) message.getData();

        String replyToTopic = context.getKafkaSwitchManagerTopic();

        try {
            installFlow(request.getSwitchId(), request.getFlowCommands());

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

        InfoMessage infoMessage = new InfoMessage(new BatchInstallResponse(), System.currentTimeMillis(),
                message.getCorrelationId());
        getKafkaProducer().sendMessageAndTrack(replyToTopic, message.getCorrelationId(), infoMessage);
    }

    private void installFlow(SwitchId switchId, List<BaseInstallFlow> commands)
            throws SwitchOperationException, FlowCommandException {
        logger.info("Do batch install flow rules on switch '{}'", switchId);

        for (BaseInstallFlow command : commands) {
            logger.debug("Processing command for switch {} {}", switchId, command);
            if (command instanceof InstallIngressFlow) {
                installIngressFlow((InstallIngressFlow) command);
            } else if (command instanceof InstallEgressFlow) {
                installEgressFlow((InstallEgressFlow) command);
            } else if (command instanceof InstallTransitFlow) {
                installTransitFlow((InstallTransitFlow) command);
            } else if (command instanceof InstallOneSwitchFlow) {
                installOneSwitchFlow((InstallOneSwitchFlow) command);
            } else {
                throw new FlowCommandException(command.getId(), command.getCookie(), command.getTransactionId(),
                        ErrorType.REQUEST_INVALID, "Unsupported command for batch install.");
            }

        }

    }

    private void doPortsCommandDataRequest(CommandMessage message) {
        ISwitchManager switchManager = context.getModuleContext().getServiceImpl(ISwitchManager.class);

        try {
            PortsCommandData request = (PortsCommandData) message.getData();
            logger.info("Getting ports data. Requester: {}", request.getRequester());
            Map<DatapathId, IOFSwitch> allSwitchMap = context.getSwitchManager().getAllSwitchMap();
            for (Map.Entry<DatapathId, IOFSwitch> entry : allSwitchMap.entrySet()) {
                SwitchId switchId = new SwitchId(entry.getKey().toString());
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
                            .requester(request.getRequester())
                            .build();

                    InfoMessage infoMessage = new InfoMessage(response, message.getTimestamp(),
                            message.getCorrelationId());
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

    private void doDeleteMeter(CommandMessage message) {
        DeleteMeterRequest request = (DeleteMeterRequest) message.getData();
        logger.info("Deleting meter '{}'. Switch: '{}'", request.getMeterId(), request.getSwitchId());

        final IKafkaProducerService producerService = getKafkaProducer();
        final String replyToTopic = context.getKafkaNorthboundTopic();

        try {
            DatapathId dpid = DatapathId.of(request.getSwitchId().toLong());
            context.getSwitchManager().deleteMeter(dpid, request.getMeterId());

            boolean deleted = context.getSwitchManager().dumpMeters(dpid)
                    .stream()
                    .noneMatch(config -> config.getMeterId() == request.getMeterId());
            DeleteMeterResponse response = new DeleteMeterResponse(deleted);
            InfoMessage infoMessage = new InfoMessage(response, System.currentTimeMillis(), message.getCorrelationId());
            producerService.sendMessageAndTrack(replyToTopic, infoMessage);
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
                .map(OfPortDescConverter::toPortDescription)
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
        String replyToTopic = context.getKafkaNorthboundTopic();
        dumpMeters(request.getSwitchId(), message.getCorrelationId(), replyToTopic, message.getTimestamp());
    }

    private void doDumpMetersForSwitchManagerRequest(CommandMessage message) {
        DumpMetersForSwitchManagerRequest request = (DumpMetersForSwitchManagerRequest) message.getData();
        String replyToTopic = context.getKafkaSwitchManagerTopic();
        dumpMeters(request.getSwitchId(), message.getCorrelationId(), replyToTopic, message.getTimestamp());
    }

    private void dumpMeters(SwitchId switchId, String correlationId, String replyToTopic, long timestamp) {
        final IKafkaProducerService producerService = getKafkaProducer();

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
            InfoMessage infoMessage = new InfoMessage(response, timestamp, correlationId);
            producerService.sendMessageAndTrack(replyToTopic, correlationId, infoMessage);
        } catch (UnsupportedSwitchOperationException e) {
            String messageString = "Not supported: " + switchId;
            logger.error(messageString, e);
            anError(ErrorType.PARAMETERS_INVALID)
                    .withMessage(e.getMessage())
                    .withDescription(messageString)
                    .withCorrelationId(correlationId)
                    .withTopic(replyToTopic)
                    .sendVia(producerService);
        } catch (SwitchNotFoundException e) {
            logger.info("Dumping switch meters is unsuccessful. Switch {} not found", switchId);
            anError(ErrorType.NOT_FOUND)
                    .withMessage(e.getMessage())
                    .withDescription(switchId.toString())
                    .withCorrelationId(correlationId)
                    .withTopic(replyToTopic)
                    .sendVia(producerService);
        } catch (SwitchOperationException e) {
            logger.error("Unable to dump meters", e);
            anError(ErrorType.NOT_FOUND)
                    .withMessage(e.getMessage())
                    .withDescription("Unable to dump meters")
                    .withCorrelationId(correlationId)
                    .withTopic(replyToTopic)
                    .sendVia(producerService);
        }
    }

    private void doModifyMeterRequest(CommandMessage message) {
        MeterModifyCommandRequest request = (MeterModifyCommandRequest) message.getData();

        final IKafkaProducerService producerService = getKafkaProducer();
        String replyToTopic = context.getKafkaNorthboundTopic();

        SwitchId fwdSwitchId = request.getFwdSwitchId();
        SwitchId rvsSwitchId = request.getRvsSwitchId();

        DatapathId fwdDpId = DatapathId.of(fwdSwitchId.toLong());
        DatapathId rvsDpId = DatapathId.of(rvsSwitchId.toLong());
        long fwdMeterId = request.getFwdMeterId();
        long rvsMeterId = request.getRvsMeterId();

        ISwitchManager switchManager = context.getSwitchManager();

        try {
            switchManager.modifyMeterForFlow(fwdDpId, fwdMeterId, request.getBandwidth());
            switchManager.modifyMeterForFlow(rvsDpId, rvsMeterId, request.getBandwidth());

            MeterEntry fwdMeterEntry = OfMeterConverter.toMeterEntry(switchManager.dumpMeterById(fwdDpId, fwdMeterId));
            MeterEntry rvsMeterEntry = OfMeterConverter.toMeterEntry(switchManager.dumpMeterById(rvsDpId, rvsMeterId));

            SwitchMeterEntries srcMeter = SwitchMeterEntries.builder()
                    .switchId(fwdSwitchId)
                    .meterEntries(ImmutableList.of(fwdMeterEntry))
                    .build();

            SwitchMeterEntries dstMeter = SwitchMeterEntries.builder()
                    .switchId(rvsSwitchId)
                    .meterEntries(ImmutableList.of(rvsMeterEntry))
                    .build();

            FlowMeterEntries response = FlowMeterEntries.builder()
                    .srcMeter(srcMeter)
                    .dstMeter(dstMeter)
                    .build();

            InfoMessage infoMessage = new InfoMessage(response, message.getTimestamp(), message.getCorrelationId());
            producerService.sendMessageAndTrack(context.getKafkaNorthboundTopic(), infoMessage);
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

    private void installMeter(DatapathId dpid, long meterId, long bandwidth, String flowId) {
        try {
            context.getSwitchManager().installMeterForFlow(dpid, bandwidth, meterId);
        } catch (UnsupportedOperationException e) {
            logger.info("Skip meter {} installation for flow {} on switch {}: {}",
                    meterId, flowId, dpid, e.getMessage());
        } catch (SwitchOperationException e) {
            logger.error("Failed to install meter {} for flow {} on switch {}: {}", meterId, flowId, dpid,
                    e.getMessage());
        }

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
                doControllerMsg(message);
            }
        } catch (Exception exception) {
            logger.error("error processing message '{}'", message, exception);
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

    public static class Factory {
        private final ConsumerContext context;
        private final List<CommandDispatcher<?>> dispatchers = ImmutableList.of(
                new PingRequestDispatcher(),
                new SetupBfdSessionDispatcher(),
                new RemoveBfdSessionDispatcher());

        public Factory(ConsumerContext context) {
            this.context = context;
        }

        public RecordHandler produce(ConsumerRecord<String, String> record) {
            return new RecordHandler(context, dispatchers, record);
        }
    }
}
