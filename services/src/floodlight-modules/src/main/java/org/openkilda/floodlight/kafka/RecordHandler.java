package org.openkilda.floodlight.kafka;

import static org.openkilda.messaging.Utils.MAPPER;
import static java.util.Arrays.asList;


import org.openkilda.floodlight.converter.IOFSwitchConverter;
import org.openkilda.floodlight.converter.OFFlowStatsConverter;
import org.openkilda.floodlight.switchmanager.ISwitchManager;
import org.openkilda.floodlight.switchmanager.MeterPool;
import org.openkilda.floodlight.switchmanager.SwitchOperationException;
import org.openkilda.messaging.Destination;
import org.openkilda.messaging.Topic;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.CommandWithReplyToMessage;
import org.openkilda.messaging.command.discovery.DiscoverIslCommandData;
import org.openkilda.messaging.command.discovery.DiscoverPathCommandData;
import org.openkilda.messaging.command.discovery.NetworkCommandData;
import org.openkilda.messaging.command.flow.BaseInstallFlow;
import org.openkilda.messaging.command.flow.InstallEgressFlow;
import org.openkilda.messaging.command.flow.InstallIngressFlow;
import org.openkilda.messaging.command.flow.InstallOneSwitchFlow;
import org.openkilda.messaging.command.flow.InstallTransitFlow;
import org.openkilda.messaging.command.flow.RemoveFlow;
import org.openkilda.messaging.command.switches.ConnectModeRequest;
import org.openkilda.messaging.command.switches.DeleteRulesAction;
import org.openkilda.messaging.command.switches.DumpRulesRequest;
import org.openkilda.messaging.command.switches.InstallRulesAction;
import org.openkilda.messaging.command.switches.SwitchRulesInstallRequest;
import org.openkilda.messaging.command.switches.InstallMissedFlowsRequest;
import org.openkilda.messaging.command.switches.SwitchRulesDeleteRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.discovery.NetworkInfoData;
import org.openkilda.messaging.info.discovery.NetworkSyncMarker;
import org.openkilda.messaging.info.event.PortChangeType;
import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.info.event.SwitchState;
import org.openkilda.messaging.info.rule.FlowEntry;
import org.openkilda.messaging.info.rule.SwitchFlowEntries;
import org.openkilda.messaging.info.switches.ConnectModeResponse;
import org.openkilda.messaging.info.switches.SwitchRulesResponse;
import org.openkilda.messaging.payload.flow.OutputVlanType;

import net.floodlightcontroller.core.IOFSwitch;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.projectfloodlight.openflow.protocol.OFFlowStatsReply;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.stream.Collectors;

class RecordHandler implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(RecordHandler.class);
    private static final String OUTPUT_FLOW_TOPIC = Topic.FLOW;
    private static final String OUTPUT_DISCO_TOPIC = Topic.TOPO_DISCO;
    private static final String TOPO_ENG_TOPIC = Topic.TOPO_ENG;

    private final ConsumerContext context;
    private final ConsumerRecord<String, String> record;
    private final MeterPool meterPool;

    public RecordHandler(ConsumerContext context, ConsumerRecord<String, String> record,
            MeterPool meterPool) {
        this.context = context;
        this.record = record;
        this.meterPool = meterPool;
    }

    protected void doControllerMsg(CommandMessage message) {
        // Define the destination topic where the reply will be sent to.
        final String replyToTopic;
        if (message instanceof CommandWithReplyToMessage) {
            replyToTopic = ((CommandWithReplyToMessage) message).getReplyTo();
        } else {
            replyToTopic = OUTPUT_FLOW_TOPIC;
        }
        final Destination replyDestination = getDestinationForTopic(replyToTopic);

        try {
            CommandData data = message.getData();
            handleCommand(message, data, replyToTopic, replyDestination);
        } catch (FlowCommandException e) {
            ErrorMessage error = new ErrorMessage(
                    e.makeErrorResponse(),
                    System.currentTimeMillis(), message.getCorrelationId(), replyDestination);
            context.getKafkaProducer().postMessage(replyToTopic, error);
        } catch (Exception e) {
            logger.error("Unhandled exception: {}", e);
        }
    }

    private void handleCommand(CommandMessage message, CommandData data, String replyToTopic,
            Destination replyDestination) throws FlowCommandException {
        if (data instanceof DiscoverIslCommandData) {
            doDiscoverIslCommand(data);
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
            doDumpRulesRequest(message, replyToTopic);
        } else if (data instanceof InstallMissedFlowsRequest) {
            doSyncRulesRequest(message);
        } else {
            logger.error("unknown data type: {}", data.toString());
        }
    }

    private Destination getDestinationForTopic(String replyToTopic) {
        //TODO: depending on the future system design, either get rid of destination or complete the switch-case.
        switch (replyToTopic) {
            case Topic.NORTHBOUND:
                return Destination.NORTHBOUND;
            default:
                return Destination.WFM_TRANSACTION;
        }
    }

    private void doDiscoverIslCommand(CommandData data) {
        DiscoverIslCommandData command = (DiscoverIslCommandData) data;
        logger.debug("sending discover ISL to {}", command);

        String switchId = command.getSwitchId();
        boolean result = context.getPathVerificationService().sendDiscoveryMessage(
                DatapathId.of(switchId), OFPort.of(command.getPortNo()));

        if (result) {
            logger.debug("packet_out was sent to {}", switchId);
        } else {
            logger.warn("packet_out was not sent to {}-{}", switchId, command.getPortNo());
        }
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
            context.getKafkaProducer().postMessage(replyToTopic, message);
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

        Long meterId = allocateMeterId(
                command.getMeterId(), command.getSwitchId(), command.getId(), command.getCookie());

        context.getSwitchManager().installMeter(
                DatapathId.of(command.getSwitchId()),
                command.getBandwidth(), 1024, meterId);

        context.getSwitchManager().installIngressFlow(
                DatapathId.of(command.getSwitchId()),
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
            context.getKafkaProducer().postMessage(replyToTopic, message);
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
                DatapathId.of(command.getSwitchId()),
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
            context.getKafkaProducer().postMessage(replyToTopic, message);
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
                DatapathId.of(command.getSwitchId()),
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
            context.getKafkaProducer().postMessage(replyToTopic, message);
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
        Long meterId = allocateMeterId(
                command.getMeterId(), command.getSwitchId(), command.getId(), command.getCookie());

        context.getSwitchManager().installMeter(
                DatapathId.of(command.getSwitchId()),
                command.getBandwidth(), 1024, meterId);

        OutputVlanType directOutputVlanType = command.getOutputVlanType();
        context.getSwitchManager().installOneSwitchFlow(
                DatapathId.of(command.getSwitchId()),
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

        DatapathId dpid = DatapathId.of(command.getSwitchId());
        ISwitchManager switchManager = context.getSwitchManager();
        try {
            switchManager.deleteFlow(dpid, command.getId(), command.getCookie());

            // FIXME(surabujin): QUICK FIX - try to drop meterPool completely
            Long meterId = command.getMeterId();
            if (meterId != null) {
                switchManager.deleteMeter(dpid, meterId);
            }

            message.setDestination(replyDestination);
            context.getKafkaProducer().postMessage(replyToTopic, message);
        } catch (SwitchOperationException e) {
            throw new FlowCommandException(command.getId(), ErrorType.DELETION_FAILURE, e);
        }
    }

    /**
     * Create network dump for OFELinkBolt
     *
     * @param message NetworkCommandData
     */
    private void doNetworkDump(final CommandMessage message) {
        logger.debug("Processing request from WFM to dump switches. {}", message.getCorrelationId());

        context.getKafkaProducer().postMessage(OUTPUT_DISCO_TOPIC,
                new InfoMessage(new NetworkSyncMarker(), System.currentTimeMillis(), message.getCorrelationId()));

        Map<DatapathId, IOFSwitch> allSwitchMap = context.getSwitchManager().getAllSwitchMap();
        final String correlationId = UUID.randomUUID().toString();

        allSwitchMap.values().stream()
                .map(this::buildSwitchInfoData)
                .forEach(sw ->
                        context.getKafkaProducer().postMessage(OUTPUT_DISCO_TOPIC,
                                new InfoMessage(sw, System.currentTimeMillis(), correlationId)));

        allSwitchMap.values().stream()
                .flatMap(sw ->
                        sw.getEnabledPorts().stream()
                                .map(port -> buildPort(sw, port))
                                .collect(Collectors.toSet())
                                .stream())
                .forEach(port ->
                        context.getKafkaProducer().postMessage(OUTPUT_DISCO_TOPIC,
                                new InfoMessage(port, System.currentTimeMillis(), correlationId)));
    }

    private void doInstallSwitchRules(final CommandMessage message, String replyToTopic, Destination replyDestination) {
        SwitchRulesInstallRequest request = (SwitchRulesInstallRequest) message.getData();
        logger.debug("Installing rules on '{}' switch: action={}", request.getSwitchId(), request.getInstallRulesAction());

        DatapathId dpid = DatapathId.of(request.getSwitchId());
        ISwitchManager switchManager = context.getSwitchManager();
        InstallRulesAction installAction = request.getInstallRulesAction();
        List<Long> installedRules = new ArrayList<>();
        try {
            if (installAction == InstallRulesAction.INSTALL_DROP ) {
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
            context.getKafkaProducer().postMessage(replyToTopic, infoMessage);

        } catch (SwitchOperationException e) {
            ErrorData errorData = new ErrorData(ErrorType.CREATION_FAILURE, e.getMessage(), request.getSwitchId());
            ErrorMessage error = new ErrorMessage(errorData,
                    System.currentTimeMillis(), message.getCorrelationId(), replyDestination);
            context.getKafkaProducer().postMessage(replyToTopic, error);
        }
    }

    private void doDeleteSwitchRules(final CommandMessage message, String replyToTopic, Destination replyDestination) {
        SwitchRulesDeleteRequest request = (SwitchRulesDeleteRequest) message.getData();
        logger.debug("Deleting rules from '{}' switch: action={}", request.getSwitchId(), request.getDeleteRulesAction());

        DatapathId dpid = DatapathId.of(request.getSwitchId());
        ISwitchManager switchManager = context.getSwitchManager();
        DeleteRulesAction deleteAction = request.getDeleteRulesAction();
        List<Long> removedRules = new ArrayList<>();
        try {
            /*
             * This first part .. we are either deleting one rule, or all non-default rules (the else)
             */
            List<Long> toRemove = new ArrayList<>();
            if (deleteAction == DeleteRulesAction.ONE ) {
                toRemove.add(request.getOneCookie());
            } else if (deleteAction == DeleteRulesAction.REMOVE_DROP) {
                toRemove.add(ISwitchManager.DROP_RULE_COOKIE);
            } else if (deleteAction == DeleteRulesAction.REMOVE_BROADCAST) {
                toRemove.add(ISwitchManager.VERIFICATION_BROADCAST_RULE_COOKIE);
            } else if (deleteAction == DeleteRulesAction.REMOVE_UNICAST) {
                toRemove.add(ISwitchManager.VERIFICATION_UNICAST_RULE_COOKIE);
            } else if (deleteAction == DeleteRulesAction.REMOVE_DEFAULTS
                    || deleteAction == DeleteRulesAction.REMOVE_ADD) {
                toRemove.add(ISwitchManager.DROP_RULE_COOKIE);
                toRemove.add(ISwitchManager.VERIFICATION_BROADCAST_RULE_COOKIE);
                toRemove.add(ISwitchManager.VERIFICATION_UNICAST_RULE_COOKIE);
            }

            // toRemove is > 0 only if we are trying to delete base rule(s).
            if (toRemove.size() > 0) {
                removedRules.addAll(switchManager.deleteRuleWithCookie(dpid, toRemove));
                if (deleteAction == DeleteRulesAction.REMOVE_ADD) {
                    switchManager.installDefaultRules(dpid);
                }
            } else {
                removedRules.addAll(switchManager.deleteAllNonDefaultRules(dpid));
                /*
                 * The Second part - only for a subset of actions.
                 */
                if (deleteAction == DeleteRulesAction.DROP) {
                    List<Long> removedDefaultRules = switchManager.deleteDefaultRules(dpid);
                    // Return removedDefaultRules as a part of the result list.
                    removedRules.addAll(removedDefaultRules);

                } else if (deleteAction == DeleteRulesAction.DROP_ADD) {
                    switchManager.deleteDefaultRules(dpid);
                    switchManager.installDefaultRules(dpid);

                } else if (deleteAction == DeleteRulesAction.OVERWRITE) {
                    switchManager.installDefaultRules(dpid);
                }
            }

            SwitchRulesResponse response = new SwitchRulesResponse(removedRules);
            InfoMessage infoMessage = new InfoMessage(response,
                    System.currentTimeMillis(), message.getCorrelationId(), replyDestination);
            context.getKafkaProducer().postMessage(replyToTopic, infoMessage);

        } catch (SwitchOperationException e) {
            ErrorData errorData = new ErrorData(ErrorType.DELETION_FAILURE, e.getMessage(), request.getSwitchId());
            ErrorMessage error = new ErrorMessage(errorData,
                    System.currentTimeMillis(), message.getCorrelationId(), replyDestination);
            context.getKafkaProducer().postMessage(replyToTopic, error);
        }
    }

    private void doConnectMode(final CommandMessage message, String replyToTopic, Destination replyDestination) {
        ConnectModeRequest request = (ConnectModeRequest) message.getData();
        if (request.getMode() != null)
            logger.debug("Setting CONNECT MODE to '{}'", request.getMode());
        else
            logger.debug("Getting CONNECT MODE");

        ISwitchManager switchManager = context.getSwitchManager();
        ConnectModeRequest.Mode result = switchManager.connectMode(request.getMode());

        logger.debug("CONNECT MODE is now '{}'", result);
        ConnectModeResponse response = new ConnectModeResponse(result);
        InfoMessage infoMessage = new InfoMessage(response,
                    System.currentTimeMillis(), message.getCorrelationId(), replyDestination);
        context.getKafkaProducer().postMessage(replyToTopic, infoMessage);

    }

    private void doDumpRulesRequest(final CommandMessage message,  String replyToTopic) {
        DumpRulesRequest request = (DumpRulesRequest) message.getData();
        final String switchId = request.getSwitchId();
        logger.debug("Loading installed rules for switch {}", switchId);

        OFFlowStatsReply reply = context.getSwitchManager().dumpFlowTable(DatapathId.of(switchId));
        List<FlowEntry> flows = reply.getEntries().stream()
                .map(OFFlowStatsConverter::toFlowEntry)
                .collect(Collectors.toList());

        SwitchFlowEntries response = SwitchFlowEntries.builder()
                .switchId(switchId)
                .flowEntries(flows)
                .build();
        InfoMessage infoMessage = new InfoMessage(response, message.getTimestamp(),
                message.getCorrelationId());
        context.getKafkaProducer().postMessage(replyToTopic, infoMessage);
    }

    /**
     * Installs missed flows on the switch.
     * @param message with list of flows.
     */
    private void doSyncRulesRequest(final CommandMessage message) {
        InstallMissedFlowsRequest request = (InstallMissedFlowsRequest) message.getData();
        final String switchId = request.getSwitchId();
        logger.debug("Processing rules to be updated for switch {}", switchId);

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
                }
            } catch (SwitchOperationException e) {
                logger.error("Error during flow installation", e);
            }
        }
    }

    private PortInfoData buildPort(IOFSwitch sw, OFPortDesc port) {
        return new PortInfoData(sw.getId().toString(), port.getPortNo().getPortNumber(), null,
                PortChangeType.UP);
    }

    private long allocateMeterId(Long meterId, String switchId, String flowId, Long cookie) {
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
        try {
            String value = (String) record.value();
            // TODO: Prior to Message changes, this MAPPER would read Message ..
            //          but, changed to BaseMessage and got an error wrt "timestamp" ..
            //          so, need to experiment with why CommandMessage can't be read as
            //          a BaseMessage
            CommandMessage message = MAPPER.readValue(value, CommandMessage.class);
            doControllerMsg(message);
        } catch (Exception exception) {
            logger.error("error parsing record={}", record.value(), exception);
        }
    }

    @Override
    public void run() {
        parseRecord(record);
    }

    protected SwitchInfoData buildSwitchInfoData(IOFSwitch sw) {
        // I don't know is that correct
        SwitchState state = sw.isActive() ? SwitchState.ACTIVATED : SwitchState.ADDED;
        return IOFSwitchConverter.buildSwitchInfoData(sw, state);
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
