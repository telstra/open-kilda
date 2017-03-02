package org.bitbucket.openkilda.floodlight.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.module.*;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.bitbucket.openkilda.floodlight.message.CommandMessage;
import org.bitbucket.openkilda.floodlight.message.Message;
import org.bitbucket.openkilda.floodlight.message.command.CommandData;
import org.bitbucket.openkilda.floodlight.message.command.DefaultFlowsCommandData;
import org.bitbucket.openkilda.floodlight.message.command.DiscoverISLCommandData;
import org.bitbucket.openkilda.floodlight.message.command.DiscoverPathCommandData;
import org.bitbucket.openkilda.floodlight.pathverification.IPathVerificationService;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class KafkaMessageCollector implements IFloodlightModule {
    private Logger logger;
    private Properties kafkaProps;
    private String topic;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private ConcurrentLinkedQueue<ConsumerRecord> newRecordQueue;
    private ObjectMapper mapper;
    private IPathVerificationService pathVerificationService;

    class Producer implements Runnable {
        private final Properties kafkaProps;

        public Producer(Properties kafkaProps) {
            this.kafkaProps = kafkaProps;
        }

        @Override
        public void run() {
            KafkaProducer<String, String> producer = new KafkaProducer<>(kafkaProps);
        }
    }

    class ParseRecord implements Runnable {

        public ParseRecord() {
            mapper = new ObjectMapper();
        }

        private ConsumerRecord dequeueItem() {
            if (!newRecordQueue.isEmpty()) {
                logger.debug("Queue size: " + newRecordQueue.size());
                return newRecordQueue.remove();
            } else {
                return null;
            }
        }

        private void doControllerMsg(CommandMessage message) {
            CommandData data = message.getData();
            if (data instanceof DefaultFlowsCommandData) {
                doDefaultFlowsCommand(data);
            } else if (data instanceof DiscoverISLCommandData) {
                doDiscoverIslCommand(data);
            } else if (data instanceof DiscoverPathCommandData) {
                doDiscoverPathCommand(data);
            } else {
                logger.error("unknown data type: {}", data.toString());
            }

        }

        private void doDefaultFlowsCommand(CommandData data) {
            DefaultFlowsCommandData command = (DefaultFlowsCommandData) data;
            logger.debug("sending default flows to {}", (command.getSwitchId()));
            pathVerificationService.installVerificationRule(DatapathId.of(command.getSwitchId()), false);
        }

        private void doDiscoverIslCommand(CommandData data) {
            DiscoverISLCommandData command = (DiscoverISLCommandData) data;
            logger.debug("sending discover ISL to {}:{}", command.getSwitchId(), command.getPortNo());
            pathVerificationService.sendDiscoveryMessage(DatapathId.of(command.getSwitchId()),
                                                         OFPort.of(command.getPortNo()));
        }

        private void doDiscoverPathCommand(CommandData data) {
            DiscoverPathCommandData command = (DiscoverPathCommandData) data;
            logger.debug("sending discover Path to {}:{} - {}",
                         new Object[]{command.getSrcSwitchId(), command.getSrcPortNo(), command.getDstSwitchId()});
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
            while (!closed.get()) {
                ConsumerRecord record = dequeueItem();
                if (record != null) {
                    parseRecord(record);
                }
            }
        }
    }


    class Consumer implements Runnable {
        final List<String> topics;
        final Properties kafkaProps;

        public Consumer(List<String> topics, Properties kafkaProps) {
            this.topics = topics;
            this.kafkaProps = kafkaProps;
        }

        @Override
        public void run() {
            KafkaConsumer<String, String> consumer = new KafkaConsumer<>(kafkaProps);
            consumer.subscribe(topics);

            while (!closed.get()) {
                ConsumerRecords<String, String> records = consumer.poll(100);
                for (ConsumerRecord<String, String> record: records) {
                    logger.debug("received message: {} - {}", new Object[]{record.offset(), record.value()});
                    newRecordQueue.add(record);
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
        Collection<Class<? extends IFloodlightService>> services = new ArrayList<>();
        services.add(IFloodlightProviderService.class);
        services.add(IPathVerificationService.class);
        return services;
    }

    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        IFloodlightProviderService floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
        pathVerificationService = context.getServiceImpl(IPathVerificationService.class);
        logger = LoggerFactory.getLogger(this.getClass());
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
        newRecordQueue = new ConcurrentLinkedQueue<>();
    }

    @Override
    public void startUp(FloodlightModuleContext floodlightModuleContext) throws FloodlightModuleException {
        logger.info("Starting {}", this.getClass().getCanonicalName());
        try {
            ExecutorService executorService = Executors.newFixedThreadPool(10);
            executorService.execute(new Consumer(Arrays.asList(topic), kafkaProps));
            executorService.execute(new ParseRecord());
        } catch (Exception exception) {
            logger.error("error", exception);
        }
    }
}
