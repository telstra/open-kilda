package org.bitbucket.openkilda.floodlight.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import net.floodlightcontroller.core.module.*;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.bitbucket.openkilda.floodlight.message.CommandMessage;
import org.bitbucket.openkilda.floodlight.message.InfoMessage;
import org.bitbucket.openkilda.floodlight.message.Message;
import org.bitbucket.openkilda.floodlight.message.command.*;
import org.bitbucket.openkilda.floodlight.message.info.FlowStatsData;
import org.bitbucket.openkilda.floodlight.message.info.InfoData;
import org.bitbucket.openkilda.floodlight.message.info.MeterConfigStatsData;
import org.bitbucket.openkilda.floodlight.message.info.PortStatsData;
import org.bitbucket.openkilda.floodlight.pathverification.IPathVerificationService;
import org.bitbucket.openkilda.floodlight.switchmanager.ISwitchManager;
import org.bitbucket.openkilda.floodlight.switchmanager.OutputVlanType;
import org.projectfloodlight.openflow.protocol.OFStatsReply;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

public class KafkaMessageCollector implements IFloodlightModule {
    private static final Logger logger = LoggerFactory.getLogger(KafkaMessageCollector.class);
    private static final String OF_TO_WFM_TOPIC = "kilda.ofs.wfm.flow";
    private Properties kafkaProps;
    private String topic;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final ObjectMapper mapper = new ObjectMapper();
    private IPathVerificationService pathVerificationService;
    private ISwitchManager switchManager;
    private KafkaMessageProducer kafkaProducer;

    class ParseRecord implements Runnable {
        final ConsumerRecord record;

        public ParseRecord(ConsumerRecord record) {
            this.record = record;
        }

        private void doControllerMsg(CommandMessage message) {
            CommandData data = message.getData();
            if (data instanceof DiscoverISLCommandData) {
                doDiscoverIslCommand(data);
            } else if (data instanceof DiscoverPathCommandData) {
                doDiscoverPathCommand(data);
            } else if (data instanceof InstallIngressFlow) {
                doInstallIngressFlow(data);
            } else if (data instanceof InstallEgressFlow) {
                doInstallEgressFlow(data);
            } else if (data instanceof InstallTransitFlow) {
                doInstallTransitFlow(data);
            } else if (data instanceof InstallOneSwitchFlow) {
                doInstallOneSwitchFlow(data);
            } else if (data instanceof DeleteFlow) {
                doDeleteFlow(((DeleteFlow) data));
            } else if (data instanceof StatsRequest) {
                doRequestStats((StatsRequest) data);
            } else {
                logger.error("unknown data type: {}", data.toString());
            }
        }

        private void doDiscoverIslCommand(CommandData data) {
            DiscoverISLCommandData command = (DiscoverISLCommandData) data;
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
            InstallIngressFlow command = (InstallIngressFlow) data;
            logger.debug("creating an ingress flow: {}",command);

            switchManager.installMeter(DatapathId.of(command.getSwitchId()),
                    command.getBandwidth().longValue(),
                    1024,
                    command.getMeterId().longValue());

            switchManager.installIngressFlow(DatapathId.of(command.getSwitchId()), command.getCookie(),
                    command.getInputPort().intValue(),
                    command.getOutputPort().intValue(),
                    command.getInputVlanId().intValue(),
                    command.getTransitVlanId().intValue(),
                    OutputVlanType.valueOf(command.getOutputVlanType()), command.getMeterId().longValue());
        }

        /**
         * doInstallEgressFlow - Installs egress flow on the switch
         *
         * @param data - Command data for flow installation
         */
        private void doInstallEgressFlow(CommandData data) {
            InstallEgressFlow command = (InstallEgressFlow) data;
            logger.debug("creating an egress flow: {}", command);

            switchManager.installEgressFlow(DatapathId.of(command.getSwitchId()), command.getCookie(),
                    command.getInputPort().intValue(),
                    command.getOutputPort().intValue(),
                    command.getTransitVlanId().intValue(),
                    command.getOutputVlanId().intValue(), OutputVlanType.valueOf(command.getOutputVlanType()));
        }

        /**
         * doInstallTransitFlow - Installs transit flow on the switch
         *
         * @param data - Command data for flow installation
         */
        private void doInstallTransitFlow(CommandData data) {
            InstallTransitFlow command = (InstallTransitFlow) data;
            logger.debug("creating a transit flow: {}", command);

            switchManager.installTransitFlow(DatapathId.of(command.getSwitchId()), command.getCookie(),
                    command.getInputPort().intValue(),
                    command.getOutputPort().intValue(), command.getTransitVlanId().intValue());
        }

        /**
         * doInstallOneSwitchFlow - Installs flow through one switch
         *
         * @param data - Command data for flow installation
         */
        private void doInstallOneSwitchFlow(CommandData data) {
            InstallOneSwitchFlow command = (InstallOneSwitchFlow) data;
            logger.debug("creating a flow through one switch: {}", command);

            switchManager.installMeter(DatapathId.of(command.getSwitchId()),
                    command.getBandwidth().longValue(),
                    1024,
                    command.getInputMeterId().longValue());

            OutputVlanType directOutputVlanType = OutputVlanType.valueOf(command.getOutputVlanType());
            switchManager.installOneSwitchFlow(DatapathId.of(command.getSwitchId()), command.getCookie(),
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
            switchManager.installOneSwitchFlow(DatapathId.of(command.getSwitchId()), command.getCookie(),
                    command.getOutputPort().intValue(),
                    command.getInputPort().intValue(),
                    command.getOutputVlanId().intValue(),
                    command.getInputVlanId().intValue(),
                    reverseOutputVlanType, command.getOutputMeterId().intValue());
        }

        private void doDeleteFlow(DeleteFlow data) {
            logger.debug("deleting a flow: {}", data);
            DatapathId dpid = DatapathId.of(data.getSwitchId());
            boolean flowDeleted = switchManager.deleteFlow(dpid, data.getCookie());
            if (flowDeleted && data.getMeterId() != null) {
                switchManager.deleteMeter(dpid, data.getMeterId());
            }
        }

        private void doRequestStats(StatsRequest request) {
            final String switchId = request.getSwitchId();
            DatapathId dpid = DatapathId.of(switchId);
            switch (request.getStatsType()) {
                case FLOWS:
                    Futures.addCallback(switchManager.requestFlowStats(dpid),
                            new RequestCallback<>(data -> new FlowStatsData(switchId, data), "flow"));
                    break;
                case PORTS:
                    Futures.addCallback(switchManager.requestPortStats(dpid),
                            new RequestCallback<>(data -> new PortStatsData(switchId, data), "port"));
                    break;
                case METERS:
                    Futures.addCallback(switchManager.requestMeterConfigStats(dpid),
                            new RequestCallback<>(data -> new MeterConfigStatsData(switchId, data), "meter config"));
                    break;
                default:
                    break;
            }
        }

        private void parseRecord(ConsumerRecord record) {
            try {
                if (record.value() instanceof String) {
                    String value = (String) record.value();
                    Message message = mapper.readValue(value, Message.class);
                    if (message instanceof CommandMessage) {
                        logger.debug("got a command message");
                        CommandMessage cmdMessage = (CommandMessage) message;
                        switch (cmdMessage.getData().getDestination()) {
                            case CONTROLLER:
                                doControllerMsg(cmdMessage);
                                break;
                            case TOPOLOGY_ENGINE:
                                break;
                            default:
                                break;
                        }
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

    private class RequestCallback<T extends OFStatsReply> implements FutureCallback<List<T>> {
        private Function<List<T>, InfoData> transform;
        private String type;

        RequestCallback(Function<List<T>, InfoData> transform, String type) {
            this.transform = transform;
            this.type = type;
        }

        @Override
        public void onSuccess(@Nonnull List<T> data) {
            try {
                String message = new InfoMessage().withData(transform.apply(data)).toJson();
                kafkaProducer.send(new ProducerRecord<>(OF_TO_WFM_TOPIC, message));
            } catch (JsonProcessingException e) {
                logger.debug("Exception serializing " + type + " stats", e);
            }
        }

        @Override
        public void onFailure(Throwable t) {
            logger.debug("Exception reading " + type + " stats", t);
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
        Collection<Class<? extends IFloodlightService>> services = new ArrayList<>(3);
        services.add(IPathVerificationService.class);
        services.add(KafkaMessageProducer.class);
        services.add(ISwitchManager.class);
        return services;
    }

    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        pathVerificationService = context.getServiceImpl(IPathVerificationService.class);
        kafkaProducer = context.getServiceImpl(KafkaMessageProducer.class);
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
