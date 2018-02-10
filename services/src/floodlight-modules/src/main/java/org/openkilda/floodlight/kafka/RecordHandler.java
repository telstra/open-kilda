package org.openkilda.floodlight.kafka;

import static org.openkilda.messaging.Utils.MAPPER;

import org.openkilda.floodlight.switchmanager.MeterPool;
import org.openkilda.floodlight.switchmanager.SwitchEventCollector;
import org.openkilda.messaging.Destination;
import org.openkilda.messaging.Topic;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.discovery.DiscoverIslCommandData;
import org.openkilda.messaging.command.discovery.DiscoverPathCommandData;
import org.openkilda.messaging.command.discovery.NetworkCommandData;
import org.openkilda.messaging.command.flow.InstallEgressFlow;
import org.openkilda.messaging.command.flow.InstallIngressFlow;
import org.openkilda.messaging.command.flow.InstallOneSwitchFlow;
import org.openkilda.messaging.command.flow.InstallTransitFlow;
import org.openkilda.messaging.command.flow.RemoveFlow;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.discovery.NetworkInfoData;
import org.openkilda.messaging.info.event.PortChangeType;
import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.info.event.SwitchState;
import org.openkilda.messaging.model.ImmutablePair;
import org.openkilda.messaging.payload.flow.OutputVlanType;

import net.floodlightcontroller.core.IOFSwitch;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

class RecordHandler implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(RecordHandler.class);
    private static final String OUTPUT_FLOW_TOPIC = Topic.FLOW;
    private static final String OUTPUT_DISCO_TOPIC = Topic.TOPO_DISCO;

    private final ConsumerContext context;
    private final ConsumerRecord<String, String> record;
    private final MeterPool meterPool = new MeterPool();

    public RecordHandler(ConsumerContext context, ConsumerRecord<String, String> record) {
        this.context = context;
        this.record = record;
    }

    protected void doControllerMsg(CommandMessage message) {
        logger.debug("\n\nreceived message: {}", message);
        try {
            CommandData data = message.getData();
            if (data instanceof DiscoverIslCommandData) {
                doDiscoverIslCommand(data);
            } else if (data instanceof DiscoverPathCommandData) {
                doDiscoverPathCommand(data);
            } else if (data instanceof InstallIngressFlow) {
                doInstallIngressFlow(message);
            } else if (data instanceof InstallEgressFlow) {
                doInstallEgressFlow(message);
            } else if (data instanceof InstallTransitFlow) {
                doInstallTransitFlow(message);
            } else if (data instanceof InstallOneSwitchFlow) {
                doInstallOneSwitchFlow(message);
            } else if (data instanceof RemoveFlow) {
                doDeleteFlow(message);
            } else if (data instanceof NetworkCommandData) {
                doNetworkDump(message);
            } else {
                logger.error("unknown data type: {}", data.toString());
            }
        } catch (Exception e) {
            logger.error("RecordHandler.doControllerMsg Exception: {}", e);
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
    private void doInstallIngressFlow(final CommandMessage message) {
        InstallIngressFlow command = (InstallIngressFlow) message.getData();
        logger.debug("Creating an ingress flow: {}", command);

        int meterId = meterPool.allocate(command.getSwitchId(), command.getId());

        ImmutablePair<Long, Boolean> meterInstalled = context.getSwitchManager().installMeter(
                DatapathId.of(command.getSwitchId()),
                command.getBandwidth(),
                1024,
                meterId);

        if (!meterInstalled.getRight()) {
            ErrorMessage error = new ErrorMessage(
                    new ErrorData(ErrorType.CREATION_FAILURE, "Could not install meter", command.getId()),
                    System.currentTimeMillis(), message.getCorrelationId(), Destination.WFM_TRANSACTION);
            context.getKafkaProducer().postMessage(OUTPUT_FLOW_TOPIC, error);
        }

        ImmutablePair<Long, Boolean> flowInstalled = context.getSwitchManager().installIngressFlow(
                DatapathId.of(command.getSwitchId()),
                command.getId(),
                command.getCookie(),
                command.getInputPort(),
                command.getOutputPort(),
                command.getInputVlanId(),
                command.getTransitVlanId(),
                command.getOutputVlanType(),
                meterId);

        if (!flowInstalled.getRight()) {
            ErrorMessage error = new ErrorMessage(
                    new ErrorData(ErrorType.CREATION_FAILURE, "Could not install ingress flow", command.getId()),
                    System.currentTimeMillis(), message.getCorrelationId(), Destination.WFM_TRANSACTION);
            context.getKafkaProducer().postMessage(OUTPUT_FLOW_TOPIC, error);
        } else {
            message.setDestination(Destination.WFM_TRANSACTION);
            context.getKafkaProducer().postMessage(OUTPUT_FLOW_TOPIC, message);
        }
    }

    /**
     * Installs egress flow on the switch.
     *
     * @param message command message for flow installation
     */
    private void doInstallEgressFlow(final CommandMessage message) {
        InstallEgressFlow command = (InstallEgressFlow) message.getData();
        logger.debug("Creating an egress flow: {}", command);

        ImmutablePair<Long, Boolean> flowInstalled = context.getSwitchManager().installEgressFlow(
                DatapathId.of(command.getSwitchId()),
                command.getId(),
                command.getCookie(),
                command.getInputPort(),
                command.getOutputPort(),
                command.getTransitVlanId(),
                command.getOutputVlanId(),
                command.getOutputVlanType());

        if (!flowInstalled.getRight()) {
            ErrorMessage error = new ErrorMessage(
                    new ErrorData(ErrorType.CREATION_FAILURE, "Could not install egress flow", command.getId()),
                    System.currentTimeMillis(), message.getCorrelationId(), Destination.WFM_TRANSACTION);
            context.getKafkaProducer().postMessage(OUTPUT_FLOW_TOPIC, error);
        } else {
            message.setDestination(Destination.WFM_TRANSACTION);
            context.getKafkaProducer().postMessage(OUTPUT_FLOW_TOPIC, message);
        }
    }

    /**
     * Installs transit flow on the switch.
     *
     * @param message command message for flow installation
     */
    private void doInstallTransitFlow(final CommandMessage message) {
        InstallTransitFlow command = (InstallTransitFlow) message.getData();
        logger.debug("Creating a transit flow: {}", command);

        ImmutablePair<Long, Boolean> flowInstalled = context.getSwitchManager().installTransitFlow(
                DatapathId.of(command.getSwitchId()),
                command.getId(),
                command.getCookie(),
                command.getInputPort(),
                command.getOutputPort(),
                command.getTransitVlanId());

        if (!flowInstalled.getRight()) {
            ErrorMessage error = new ErrorMessage(
                    new ErrorData(ErrorType.CREATION_FAILURE, "Could not install transit flow", command.getId()),
                    System.currentTimeMillis(), message.getCorrelationId(), Destination.WFM_TRANSACTION);
            context.getKafkaProducer().postMessage(OUTPUT_FLOW_TOPIC, error);
        } else {
            message.setDestination(Destination.WFM_TRANSACTION);
            context.getKafkaProducer().postMessage(OUTPUT_FLOW_TOPIC, message);
        }
    }

    /**
     * Installs flow through one switch.
     *
     * @param message command message for flow installation
     */
    private void doInstallOneSwitchFlow(final CommandMessage message) {
        InstallOneSwitchFlow command = (InstallOneSwitchFlow) message.getData();
        logger.debug("creating a flow through one switch: {}", command);

        int meterId = meterPool.allocate(command.getSwitchId(), command.getId());

        ImmutablePair<Long, Boolean> meterInstalled = context.getSwitchManager().installMeter(
                DatapathId.of(command.getSwitchId()),
                command.getBandwidth(),
                1024,
                meterId);

        if (!meterInstalled.getRight()) {
            ErrorMessage error = new ErrorMessage(
                    new ErrorData(ErrorType.CREATION_FAILURE, "Could not install meter", command.getId()),
                    System.currentTimeMillis(), message.getCorrelationId(), Destination.WFM_TRANSACTION);
            context.getKafkaProducer().postMessage(OUTPUT_FLOW_TOPIC, error);
        }

        OutputVlanType directOutputVlanType = command.getOutputVlanType();
        ImmutablePair<Long, Boolean> forwardFlowInstalled = context.getSwitchManager().installOneSwitchFlow(
                DatapathId.of(command.getSwitchId()),
                command.getId(),
                command.getCookie(),
                command.getInputPort(),
                command.getOutputPort(),
                command.getInputVlanId(),
                command.getOutputVlanId(),
                directOutputVlanType,
                meterId);

        if (!forwardFlowInstalled.getRight()) {
            ErrorMessage error = new ErrorMessage(
                    new ErrorData(ErrorType.CREATION_FAILURE, "Could not install flow", command.getId()),
                    System.currentTimeMillis(), message.getCorrelationId(), Destination.WFM_TRANSACTION);
            context.getKafkaProducer().postMessage(OUTPUT_FLOW_TOPIC, error);
        } else {
            message.setDestination(Destination.WFM_TRANSACTION);
            context.getKafkaProducer().postMessage(OUTPUT_FLOW_TOPIC, message);
        }
    }

    /**
     * Removes flow.
     *
     * @param message command message for flow installation
     */
    private void doDeleteFlow(final CommandMessage message) {
        RemoveFlow command = (RemoveFlow) message.getData();
        logger.debug("deleting a flow: {}", command);

        DatapathId dpid = DatapathId.of(command.getSwitchId());
        ImmutablePair<Long, Boolean> flowDeleted = context.getSwitchManager().deleteFlow(
                dpid, command.getId(), command.getCookie());

        if (!flowDeleted.getRight()) {
            ErrorMessage error = new ErrorMessage(
                    new ErrorData(ErrorType.DELETION_FAILURE, "Could not delete flow", command.getId()),
                    System.currentTimeMillis(), message.getCorrelationId(), Destination.WFM_TRANSACTION);
            context.getKafkaProducer().postMessage(OUTPUT_FLOW_TOPIC, error);
        } else {
            message.setDestination(Destination.WFM_TRANSACTION);
            context.getKafkaProducer().postMessage(OUTPUT_FLOW_TOPIC, message);
        }

        Integer meterId = meterPool.deallocate(command.getSwitchId(), command.getId());

        if (flowDeleted.getRight() && meterId != null) {
            ImmutablePair<Long, Boolean> meterDeleted = context.getSwitchManager().deleteMeter(dpid, meterId);
            if (!meterDeleted.getRight()) {
                ErrorMessage error = new ErrorMessage(
                        new ErrorData(ErrorType.DELETION_FAILURE, "Could not delete meter", command.getId()),
                        System.currentTimeMillis(), message.getCorrelationId(), Destination.WFM_TRANSACTION);
                context.getKafkaProducer().postMessage(OUTPUT_FLOW_TOPIC, error);
            }
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

    public static class Factory {
        private final ConsumerContext context;

        public Factory(ConsumerContext context) {
            this.context = context;
        }

        public RecordHandler produce(ConsumerRecord<String, String> record) {
            return new RecordHandler(context, record);
        }
    }

    protected SwitchInfoData buildSwitchInfoData(IOFSwitch sw)
    {
        // I don't know is that correct
        SwitchState state = sw.isActive() ? SwitchState.ACTIVATED : SwitchState.ADDED;
        return SwitchEventCollector.buildSwitchInfoData(sw, state);
    }
}
