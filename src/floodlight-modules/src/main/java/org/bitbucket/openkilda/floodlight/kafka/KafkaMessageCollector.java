package org.bitbucket.openkilda.floodlight.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.bitbucket.openkilda.floodlight.pathverification.IPathVerificationService;
import org.bitbucket.openkilda.floodlight.switchmanager.ISwitchManager;
import org.bitbucket.openkilda.messaging.Message;
import org.bitbucket.openkilda.messaging.command.CommandData;
import org.bitbucket.openkilda.messaging.command.CommandMessage;
import org.bitbucket.openkilda.messaging.command.DeleteFlow;
import org.bitbucket.openkilda.messaging.command.discovery.DiscoverIslCommandData;
import org.bitbucket.openkilda.messaging.command.discovery.DiscoverPathCommandData;
import org.bitbucket.openkilda.messaging.command.flow.InstallEgressFlowCommandData;
import org.bitbucket.openkilda.messaging.command.flow.InstallIngressFlowCommandData;
import org.bitbucket.openkilda.messaging.command.flow.InstallOneSwitchFlowCommandData;
import org.bitbucket.openkilda.messaging.command.flow.InstallTransitFlowCommandData;
import org.bitbucket.openkilda.messaging.payload.response.OutputVlanType;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class KafkaMessageCollector implements IFloodlightModule {
    private static final Logger logger = LoggerFactory.getLogger(KafkaMessageCollector.class);
    private Properties kafkaProps;
    private String topic;
    private final ObjectMapper mapper = new ObjectMapper();
    private IPathVerificationService pathVerificationService;
    private ISwitchManager switchManager;

    class ParseRecord implements Runnable {
        final ConsumerRecord record;

        public ParseRecord(ConsumerRecord record) {
            this.record = record;
        }

        private void doControllerMsg(CommandMessage message) {
            CommandData data = message.getData();
            if (data instanceof DiscoverIslCommandData) {
                doDiscoverIslCommand(data);
            } else if (data instanceof DiscoverPathCommandData) {
                doDiscoverPathCommand(data);
            } else if (data instanceof InstallIngressFlowCommandData) {
                doInstallIngressFlow(data);
            } else if (data instanceof InstallEgressFlowCommandData) {
                doInstallEgressFlow(data);
            } else if (data instanceof InstallTransitFlowCommandData) {
                doInstallTransitFlow(data);
            } else if (data instanceof InstallOneSwitchFlowCommandData) {
                doInstallOneSwitchFlow(data);
            } else if (data instanceof DeleteFlow) {
                doDeleteFlow(((DeleteFlow) data));
            } else {
                logger.error("unknown data type: {}", data.toString());
            }
        }

        private void doDiscoverIslCommand(CommandData data) {
            DiscoverIslCommandData command = (DiscoverIslCommandData) data;
            logger.debug("sending discover ISL to {}", command);
            pathVerificationService.sendDiscoveryMessage(DatapathId.of(command.getSwitchId()),
                                                         OFPort.of(command.getPortNo()));
        }

        private void doDiscoverPathCommand(CommandData data) {
            DiscoverPathCommandData command = (DiscoverPathCommandData) data;
            logger.debug("sending discover Path to {}", command);
        }

        /**
         * doInstallIngressFlow - Installs ingress flow on the switch
         *
         * @param data - Command data for flow installation
         */
        private void doInstallIngressFlow(CommandData data) {
            InstallIngressFlowCommandData command = (InstallIngressFlowCommandData) data;
            logger.debug("creating an ingress flow: {}",command);

            switchManager.installMeter(DatapathId.of(command.getSwitchId()),
                    command.getBandwidth().longValue(),
                    1024,
                    command.getMeterId().longValue());

            switchManager.installIngressFlow(DatapathId.of(command.getSwitchId()), command.getFlowName(),
                    command.getInputPort().intValue(),
                    command.getOutputPort().intValue(),
                    command.getInputVlanId().intValue(),
                    command.getTransitVlanId().intValue(),
                    command.getOutputVlanType(), command.getMeterId().longValue());
        }

        /**
         * doInstallEgressFlow - Installs egress flow on the switch
         *
         * @param data - Command data for flow installation
         */
        private void doInstallEgressFlow(CommandData data) {
            InstallEgressFlowCommandData command = (InstallEgressFlowCommandData) data;
            logger.debug("creating an egress flow: {}", command);

            switchManager.installEgressFlow(DatapathId.of(command.getSwitchId()), command.getFlowName(),
                    command.getInputPort().intValue(),
                    command.getOutputPort().intValue(),
                    command.getTransitVlanId().intValue(),
                    command.getOutputVlanId().intValue(), command.getOutputVlanType());
        }

        /**
         * doInstallTransitFlow - Installs transit flow on the switch
         *
         * @param data - Command data for flow installation
         */
        private void doInstallTransitFlow(CommandData data) {
            InstallTransitFlowCommandData command = (InstallTransitFlowCommandData) data;
            logger.debug("creating a transit flow: {}", command);

            switchManager.installTransitFlow(DatapathId.of(command.getSwitchId()), command.getFlowName(),
                    command.getInputPort().intValue(),
                    command.getOutputPort().intValue(), command.getTransitVlanId().intValue());
        }

        /**
         * doInstallOneSwitchFlow - Installs flow through one switch
         *
         * @param data - Command data for flow installation
         */
        private void doInstallOneSwitchFlow(CommandData data) {
            InstallOneSwitchFlowCommandData command = (InstallOneSwitchFlowCommandData) data;
            logger.debug("creating a flow through one switch: {}", command);

            switchManager.installMeter(DatapathId.of(command.getSwitchId()),
                    command.getBandwidth().longValue(),
                    1024,
                    command.getInputMeterId().longValue());

            OutputVlanType directOutputVlanType = command.getOutputVlanType();
            switchManager.installOneSwitchFlow(DatapathId.of(command.getSwitchId()), command.getFlowName(),
                    command.getInputPort().intValue(),
                    command.getOutputPort().intValue(),
                    command.getInputVlanId().intValue(),
                    command.getOutputVlanId().intValue(),
                    directOutputVlanType, command.getInputMeterId().intValue());

            switchManager.installMeter(DatapathId.of(command.getSwitchId()),
                    command.getBandwidth().longValue(),
                    1024,
                    command.getOutputMeterId().longValue());

            OutputVlanType reverseOutputVlanType;
            switch (directOutputVlanType) {
                case POP:
                    reverseOutputVlanType = OutputVlanType.PUSH;
                    break;
                case PUSH:
                    reverseOutputVlanType = OutputVlanType.POP;
                    break;
                default:
                    reverseOutputVlanType = directOutputVlanType;
                    break;
            }
            switchManager.installOneSwitchFlow(DatapathId.of(command.getSwitchId()), command.getFlowName(),
                    command.getOutputPort().intValue(),
                    command.getInputPort().intValue(),
                    command.getOutputVlanId().intValue(),
                    command.getInputVlanId().intValue(),
                    reverseOutputVlanType, command.getOutputMeterId().intValue());
        }

        private void doDeleteFlow(DeleteFlow data) {
            logger.debug("deleting a flow: {}", data);
            DatapathId dpid = DatapathId.of(data.getSwitchId());
            boolean flowDeleted = switchManager.deleteFlow(dpid, data.getFlowName());
            if (flowDeleted && data.getMeterId() != null) {
                switchManager.deleteMeter(dpid, data.getMeterId());
            }
        }

        private void parseRecord(ConsumerRecord record) {
            try {
                if (record.value() instanceof String) {
                    String value = (String) record.value();
                    Message message = mapper.readValue(value, Message.class);
                    if (message instanceof CommandMessage) {
                        logger.debug("got a command message");
                        doControllerMsg((CommandMessage) message);
                    }
                } else {
                    logger.error("{} not of type String", record.value());
                }
            } catch (Exception exception) {
                logger.error("error parsing record.", exception);
            }
        }

        @Override
        public void run() {
            parseRecord(record);
        }
    }


    class Consumer implements Runnable {
        final List<String> topics;
        final Properties kafkaProps;
        final ExecutorService parseRecordExecutor;

        public Consumer(List<String> topics, Properties kafkaProps, ExecutorService parseRecordExecutor) {
            this.topics = topics;
            this.kafkaProps = kafkaProps;
            this.parseRecordExecutor = parseRecordExecutor;
        }

        @Override
        public void run() {
            KafkaConsumer<String, String> consumer = new KafkaConsumer<>(kafkaProps);
            consumer.subscribe(topics);

            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(100);
                for (ConsumerRecord<String, String> record: records) {
                    logger.debug("received message: {} - {}", record.offset(), record.value());
                    parseRecordExecutor.execute(new ParseRecord(record));
                }
            }
        }
    }

    /**
     * IFloodLightModule Methods
     */
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        return null;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        return null;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> services = new ArrayList<>(2);
        services.add(IPathVerificationService.class);
        services.add(ISwitchManager.class);
        return services;
    }

    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        pathVerificationService = context.getServiceImpl(IPathVerificationService.class);
        switchManager = context.getServiceImpl(ISwitchManager.class);
        Map<String, String> configParameters = context.getConfigParams(this);
        kafkaProps = new Properties();
        kafkaProps.put("bootstrap.servers", configParameters.get("bootstrap-servers"));
        kafkaProps.put("group.id", "kilda-message-collector");
        kafkaProps.put("enable.auto.commit", "true");
//      kafkaProps.put("auto.commit.interval.ms", "1000");
        kafkaProps.put("session.timeout.ms", "30000");
        kafkaProps.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        kafkaProps.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        topic = configParameters.get("topic");
    }

    @Override
    public void startUp(FloodlightModuleContext floodlightModuleContext) throws FloodlightModuleException {
        logger.info("Starting {}", this.getClass().getCanonicalName());
        try {
            ExecutorService parseRecordExecutor = Executors.newFixedThreadPool(10);
            ExecutorService consumerExecutor = Executors.newSingleThreadExecutor();
            consumerExecutor.execute(new Consumer(Collections.singletonList(topic), kafkaProps, parseRecordExecutor));
        } catch (Exception exception) {
            logger.error("error", exception);
        }
    }
}
