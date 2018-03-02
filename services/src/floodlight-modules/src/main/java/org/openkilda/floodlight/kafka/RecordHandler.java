package org.openkilda.floodlight.kafka;

import static org.openkilda.messaging.Utils.MAPPER;

import org.openkilda.floodlight.switchmanager.ISwitchManager;
import org.openkilda.floodlight.switchmanager.MeterPool;
import org.openkilda.floodlight.switchmanager.SwitchEventCollector;
import org.openkilda.floodlight.switchmanager.SwitchOperationException;
import org.openkilda.messaging.Destination;
import org.openkilda.messaging.Topic;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.discovery.DiscoverIslCommandData;
import org.openkilda.messaging.command.discovery.DiscoverPathCommandData;
import org.openkilda.messaging.command.discovery.NetworkCommandData;
import org.openkilda.messaging.command.discovery.PortsCommandData;
import org.openkilda.messaging.command.flow.InstallEgressFlow;
import org.openkilda.messaging.command.flow.InstallIngressFlow;
import org.openkilda.messaging.command.flow.InstallOneSwitchFlow;
import org.openkilda.messaging.command.flow.InstallTransitFlow;
import org.openkilda.messaging.command.flow.RemoveFlow;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.discovery.NetworkInfoData;
import org.openkilda.messaging.info.discovery.SwitchPortsData;
import org.openkilda.messaging.info.event.PortChangeType;
import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.info.event.SwitchState;
import org.openkilda.messaging.payload.flow.OutputVlanType;

import net.floodlightcontroller.core.IOFSwitch;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFPortState;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
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
            } else if (data instanceof PortsCommandData) {
                doPortsDump(message);
            } else {
                logger.error("unknown data type: {}", data.toString());
            }
        } catch (FlowCommandException e) {
            ErrorMessage error = new ErrorMessage(
                    e.makeErrorResponse(),
                    System.currentTimeMillis(), message.getCorrelationId(), Destination.WFM_TRANSACTION);
            context.getKafkaProducer().postMessage(OUTPUT_FLOW_TOPIC, error);
        } catch (Exception e) {
            logger.error("Unhandled exception: {}", e);
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
    private void doInstallIngressFlow(final CommandMessage message) throws FlowCommandException {
        InstallIngressFlow command = (InstallIngressFlow) message.getData();
        logger.debug("Creating an ingress flow: {}", command);

        int meterId = meterPool.allocate(command.getSwitchId(), command.getId());

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

            message.setDestination(Destination.WFM_TRANSACTION);
            context.getKafkaProducer().postMessage(OUTPUT_FLOW_TOPIC, message);
        } catch (SwitchOperationException e) {
            throw new FlowCommandException(command.getId(), ErrorType.CREATION_FAILURE, e);
        }
    }

    /**
     * Installs egress flow on the switch.
     *
     * @param message command message for flow installation
     */
    private void doInstallEgressFlow(final CommandMessage message) throws FlowCommandException {
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

            message.setDestination(Destination.WFM_TRANSACTION);
            context.getKafkaProducer().postMessage(OUTPUT_FLOW_TOPIC, message);
        } catch (SwitchOperationException e) {
            throw new FlowCommandException(command.getId(), ErrorType.CREATION_FAILURE, e);
        }
    }

    /**
     * Installs transit flow on the switch.
     *
     * @param message command message for flow installation
     */
    private void doInstallTransitFlow(final CommandMessage message) throws FlowCommandException {
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

            message.setDestination(Destination.WFM_TRANSACTION);
            context.getKafkaProducer().postMessage(OUTPUT_FLOW_TOPIC, message);
        } catch (SwitchOperationException e) {
            throw new FlowCommandException(command.getId(), ErrorType.CREATION_FAILURE, e);
        }
    }

    /**
     * Installs flow through one switch.
     *
     * @param message command message for flow installation
     */
    private void doInstallOneSwitchFlow(final CommandMessage message) throws FlowCommandException {
        InstallOneSwitchFlow command = (InstallOneSwitchFlow) message.getData();
        logger.debug("creating a flow through one switch: {}", command);

        int meterId = meterPool.allocate(command.getSwitchId(), command.getId());

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

            message.setDestination(Destination.WFM_TRANSACTION);
            context.getKafkaProducer().postMessage(OUTPUT_FLOW_TOPIC, message);
        } catch (SwitchOperationException e) {
            throw new FlowCommandException(command.getId(), ErrorType.CREATION_FAILURE, e);
        }
    }

    /**
     * Removes flow.
     *
     * @param message command message for flow installation
     */
    private void doDeleteFlow(final CommandMessage message) throws FlowCommandException {
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

            message.setDestination(Destination.WFM_TRANSACTION);
            context.getKafkaProducer().postMessage(OUTPUT_FLOW_TOPIC, message);
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

    private void doPortsDump(final CommandMessage message) {
        logger.info("Creating ports dump");
        PortsCommandData command = (PortsCommandData) message.getData();

        Map<DatapathId, IOFSwitch> allSwitchMap = context.getSwitchManager().getAllSwitchMap();

        Set<PortInfoData> portsInfoData = allSwitchMap
                .values()
                .stream()
                .flatMap(sw -> getPortInfo(sw, true).stream())
                .collect(Collectors.toSet());

        InfoMessage infoMessage = buildInfoMessage(new SwitchPortsData(portsInfoData, command.getRequester()),
                message.getCorrelationId());

        context.getKafkaProducer().postMessage(OUTPUT_DISCO_TOPIC,
                buildInfoMessage(new SwitchPortsData(portsInfoData, command.getRequester()),
                        message.getCorrelationId()));
    }

    private Set<PortInfoData> getPortInfo(IOFSwitch sw, boolean allPorts) {
        Collection<OFPortDesc> ports;
        if (allPorts) {
            ports = sw.getPorts();
        } else {
            ports = sw.getEnabledPorts();
        }

        return ports.stream().map(port -> buildPortInfoData(sw, port)).collect(Collectors.toSet());
    }

    private PortInfoData buildPortInfoData(IOFSwitch sw, OFPortDesc port) {
        return new PortInfoData(sw.getId().toString(),
                port.getPortNo().getPortNumber(),
                null,
                port.getState().equals(OFPortState.LINK_DOWN) ? PortChangeType.DOWN : PortChangeType.UP);
    }

    private InfoMessage buildInfoMessage(InfoData data, String correlationId) {
        return new InfoMessage(data, System.currentTimeMillis(), correlationId, Destination.WFM);
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

        public Factory(ConsumerContext context) {
            this.context = context;
        }

        public RecordHandler produce(ConsumerRecord<String, String> record) {
            return new RecordHandler(context, record);
        }
    }
}
