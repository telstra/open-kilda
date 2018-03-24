package org.openkilda.floodlight.kafka;

import static org.openkilda.messaging.Utils.MAPPER;
import static org.openkilda.messaging.command.switches.DefaultRulesAction.DROP;
import static org.openkilda.messaging.command.switches.DefaultRulesAction.DROP_ADD;
import static org.openkilda.messaging.command.switches.DefaultRulesAction.OVERWRITE;

import org.openkilda.floodlight.switchmanager.ISwitchManager;
import org.openkilda.floodlight.switchmanager.MeterPool;
import org.openkilda.floodlight.switchmanager.SwitchEventCollector;
import org.openkilda.floodlight.switchmanager.SwitchOperationException;
import org.openkilda.messaging.Destination;
import org.openkilda.messaging.Topic;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.CommandWithReplyToMessage;
import org.openkilda.messaging.command.discovery.DiscoverIslCommandData;
import org.openkilda.messaging.command.discovery.DiscoverPathCommandData;
import org.openkilda.messaging.command.discovery.NetworkCommandData;
import org.openkilda.messaging.command.flow.InstallEgressFlow;
import org.openkilda.messaging.command.flow.InstallIngressFlow;
import org.openkilda.messaging.command.flow.InstallOneSwitchFlow;
import org.openkilda.messaging.command.flow.InstallTransitFlow;
import org.openkilda.messaging.command.flow.RemoveFlow;
import org.openkilda.messaging.command.switches.DefaultRulesAction;
import org.openkilda.messaging.command.switches.SwitchRulesDeleteRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.discovery.NetworkInfoData;
import org.openkilda.messaging.info.event.PortChangeType;
import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.info.event.SwitchState;
import org.openkilda.messaging.info.switches.SwitchRulesResponse;
import org.openkilda.messaging.payload.flow.OutputVlanType;

import net.floodlightcontroller.core.IOFSwitch;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

class RecordHandler implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(RecordHandler.class);
    private static final String OUTPUT_FLOW_TOPIC = Topic.FLOW;
    private static final String OUTPUT_DISCO_TOPIC = Topic.TOPO_DISCO;

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
            if (data instanceof DiscoverIslCommandData) {
                doDiscoverIslCommand(data);
            } else if (data instanceof DiscoverPathCommandData) {
                doDiscoverPathCommand(data);
            } else if (data instanceof InstallIngressFlow) {
                doInstallIngressFlow(message, replyToTopic, replyDestination);
            } else if (data instanceof InstallEgressFlow) {
                doInstallEgressFlow(message, replyToTopic, replyDestination);
            } else if (data instanceof InstallTransitFlow) {
                doInstallTransitFlow(message, replyToTopic, replyDestination);
            } else if (data instanceof InstallOneSwitchFlow) {
                doInstallOneSwitchFlow(message, replyToTopic, replyDestination);
            } else if (data instanceof RemoveFlow) {
                doDeleteFlow(message, replyToTopic, replyDestination);
            } else if (data instanceof NetworkCommandData) {
                doNetworkDump(message);
            } else if (data instanceof SwitchRulesDeleteRequest) {
                doDeleteSwitchRules(message, replyToTopic, replyDestination);
            } else {
                logger.error("unknown data type: {}", data.toString());
            }
        } catch (FlowCommandException e) {
            ErrorMessage error = new ErrorMessage(
                    e.makeErrorResponse(),
                    System.currentTimeMillis(), message.getCorrelationId(), replyDestination);
            context.getKafkaProducer().postMessage(replyToTopic, error);
        } catch (Exception e) {
            logger.error("Unhandled exception: {}", e);
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
     * Installs ingress flow on the switch.
     *
     * @param message command message for flow installation
     */
    private void doInstallIngressFlow(final CommandMessage message, String replyToTopic, Destination replyDestination)
            throws FlowCommandException {
        InstallIngressFlow command = (InstallIngressFlow) message.getData();
        logger.debug("Creating an ingress flow: {}", command);

        Long meterId = command.getMeterId();
        if (meterId == null) {
            logger.error("Meter_id should be passed within ingress flow command. Cookie is {}", command.getCookie());
            meterId = (long) meterPool.allocate(command.getSwitchId(), command.getId());
            logger.error("Allocated meter_id {} for cookie {}", meterId, command.getCookie());
        }

        try {
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

            message.setDestination(replyDestination);
            context.getKafkaProducer().postMessage(replyToTopic, message);
        } catch (SwitchOperationException e) {
            throw new FlowCommandException(command.getId(), ErrorType.CREATION_FAILURE, e);
        }
    }

    /**
     * Installs egress flow on the switch.
     *
     * @param message command message for flow installation
     */
    private void doInstallEgressFlow(final CommandMessage message, String replyToTopic, Destination replyDestination)
            throws FlowCommandException {
        InstallEgressFlow command = (InstallEgressFlow) message.getData();
        logger.debug("Creating an egress flow: {}", command);

        try {
            context.getSwitchManager().installEgressFlow(
                    DatapathId.of(command.getSwitchId()),
                    command.getId(),
                    command.getCookie(),
                    command.getInputPort(),
                    command.getOutputPort(),
                    command.getTransitVlanId(),
                    command.getOutputVlanId(),
                    command.getOutputVlanType());

            message.setDestination(replyDestination);
            context.getKafkaProducer().postMessage(replyToTopic, message);
        } catch (SwitchOperationException e) {
            throw new FlowCommandException(command.getId(), ErrorType.CREATION_FAILURE, e);
        }
    }

    /**
     * Installs transit flow on the switch.
     *
     * @param message command message for flow installation
     */
    private void doInstallTransitFlow(final CommandMessage message, String replyToTopic, Destination replyDestination)
            throws FlowCommandException {
        InstallTransitFlow command = (InstallTransitFlow) message.getData();
        logger.debug("Creating a transit flow: {}", command);

        try {
            context.getSwitchManager().installTransitFlow(
                    DatapathId.of(command.getSwitchId()),
                    command.getId(),
                    command.getCookie(),
                    command.getInputPort(),
                    command.getOutputPort(),
                    command.getTransitVlanId());

            message.setDestination(replyDestination);
            context.getKafkaProducer().postMessage(replyToTopic, message);
        } catch (SwitchOperationException e) {
            throw new FlowCommandException(command.getId(), ErrorType.CREATION_FAILURE, e);
        }
    }

    /**
     * Installs flow through one switch.
     *
     * @param message command message for flow installation
     */
    private void doInstallOneSwitchFlow(final CommandMessage message,
            String replyToTopic, Destination replyDestination) throws FlowCommandException {
        InstallOneSwitchFlow command = (InstallOneSwitchFlow) message.getData();
        logger.debug("creating a flow through one switch: {}", command);

        Long meterId = command.getMeterId();
        if (meterId == null) {
            logger.error("Meter_id should be passed within one switch flow command. Cookie is {}", command.getCookie());
            meterId = (long) meterPool.allocate(command.getSwitchId(), command.getId());
            logger.error("Allocated meter_id {} for cookie {}", meterId, command.getCookie());
        }

        try {
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

            message.setDestination(replyDestination);
            context.getKafkaProducer().postMessage(replyToTopic, message);
        } catch (SwitchOperationException e) {
            throw new FlowCommandException(command.getId(), ErrorType.CREATION_FAILURE, e);
        }
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

            Integer meterId = meterPool.deallocate(command.getSwitchId(), command.getId());
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
        logger.info("Create network dump");
        NetworkCommandData command = (NetworkCommandData) message.getData();

        Map<DatapathId, IOFSwitch> allSwitchMap = context.getSwitchManager().getAllSwitchMap();

        Set<SwitchInfoData> switchesInfoData = allSwitchMap.values().stream().map(
                this::buildSwitchInfoData).collect(Collectors.toSet());

        Set<PortInfoData> portsInfoData = allSwitchMap.values().stream().flatMap(sw ->
                sw.getEnabledPorts().stream().map( port ->
                        new PortInfoData(sw.getId().toString(), port.getPortNo().getPortNumber(), null,
                        PortChangeType.UP)
                    ).collect(Collectors.toSet()).stream())
                .collect(Collectors.toSet());

        NetworkInfoData dump = new NetworkInfoData(
                command.getRequester(),
                switchesInfoData,
                portsInfoData,
                Collections.emptySet(),
                Collections.emptySet());

        InfoMessage infoMessage = new InfoMessage(dump, System.currentTimeMillis(),
                message.getCorrelationId());

        context.getKafkaProducer().postMessage(OUTPUT_DISCO_TOPIC, infoMessage);
    }

    private void doDeleteSwitchRules(final CommandMessage message, String replyToTopic, Destination replyDestination) {
        SwitchRulesDeleteRequest request = (SwitchRulesDeleteRequest) message.getData();
        logger.debug("Deleting rules from '{}' switch", request.getSwitchId());

        DatapathId dpid = DatapathId.of(request.getSwitchId());
        ISwitchManager switchManager = context.getSwitchManager();
        try {
            List<Long> removedRules = switchManager.deleteAllNonDefaultRules(dpid);

            DefaultRulesAction defaultRulesAction = request.getDefaultRulesAction();
            if (defaultRulesAction == DROP) {
                List<Long> removedDefaultRules = switchManager.deleteDefaultRules(dpid);
                // Return removedDefaultRules as a part of the result list.
                removedRules.addAll(removedDefaultRules);

            } else if (defaultRulesAction == DROP_ADD) {
                switchManager.deleteDefaultRules(dpid);
                switchManager.installDefaultRules(dpid);

            } else if (defaultRulesAction == OVERWRITE) {
                switchManager.installDefaultRules(dpid);
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

    protected SwitchInfoData buildSwitchInfoData(IOFSwitch sw)
    {
        // I don't know is that correct
        SwitchState state = sw.isActive() ? SwitchState.ACTIVATED : SwitchState.ADDED;
        return SwitchEventCollector.buildSwitchInfoData(sw, state);
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
