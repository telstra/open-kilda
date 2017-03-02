package org.bitbucket.openkilda.floodlight.switchmanager;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.PortChangeType;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.bitbucket.openkilda.floodlight.message.InfoMessage;
import org.bitbucket.openkilda.floodlight.message.Message;
import org.bitbucket.openkilda.floodlight.message.info.InfoData;
import org.bitbucket.openkilda.floodlight.message.info.PortInfoData;
import org.bitbucket.openkilda.floodlight.message.info.SwitchInfoData;
import org.bitbucket.openkilda.floodlight.message.info.SwitchInfoData.SwitchEventType;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SwitchEventCollector implements IFloodlightModule, IOFSwitchListener {

    private IOFSwitchService switchService;
    private Logger logger;
    private ConcurrentLinkedQueue<Message> queue;
    private Properties kafkaProps;
    private String topic;

    /**
     * IOFSwitchListener methods
     */

    @Override
    public void switchAdded(DatapathId switchId) {
        Message message = buildSwitchMessage(switchId, SwitchEventType.ADDED);
        queue.add(message);
    }

    @Override
    public void switchRemoved(DatapathId switchId) {
        Message message = buildSwitchMessage(switchId, SwitchEventType.REMOVED);
        queue.add(message);
    }

    @Override
    public void switchActivated(DatapathId switchId) {
        Message message = buildSwitchMessage(switchId, SwitchEventType.ACTIVATED);
        queue.add(message);

        IOFSwitch sw = switchService.getSwitch(switchId);
        if (sw.getEnabledPortNumbers() != null) {
            for (OFPort p : sw.getEnabledPortNumbers()) {
                queue.add(buildPortMessage(sw.getId(), p, PortChangeType.UP));
            }
        }
    }

    @Override
    public void switchPortChanged(DatapathId switchId, OFPortDesc port, PortChangeType type) {
        Message message = buildPortMessage(switchId, port, type);
        queue.add(message);
    }

    @Override
    public void switchChanged(DatapathId switchId) {
        Message message = buildSwitchMessage(switchId, SwitchEventType.CHANGED);
        queue.add(message);
    }

    @Override
    public void switchDeactivated(DatapathId switchId) {
        Message message = buildSwitchMessage(switchId, SwitchEventType.DEACTIVATED);
        queue.add(message);
    }

    /**
     * IFloodlightModule methods
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
        services.add(IOFSwitchService.class);
        return services;
    }

    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        IFloodlightProviderService floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
        switchService = context.getServiceImpl(IOFSwitchService.class);
        logger = LoggerFactory.getLogger(SwitchEventCollector.class);
        queue = new ConcurrentLinkedQueue<>();

        Map<String, String> configParameters = context.getConfigParams(this);
        kafkaProps = new Properties();
        kafkaProps.put("bootstrap.servers", configParameters.get("bootstrap-servers"));
        kafkaProps.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        kafkaProps.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        topic = configParameters.get("topic");
    }

    @Override
    public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
        logger.info("Starting " + SwitchEventCollector.class.getCanonicalName());
        switchService.addOFSwitchListener(this);
        ExecutorService executor = Executors.newFixedThreadPool(5);
        try {
            executor.execute(new Producer());
        }  catch (Exception exception) {
            logger.error("Exception: ", exception);
            executor.execute(new Producer());
        }
    }

    /**
     * Utility functions
     */

    private Message buildSwitchMessage(DatapathId dpid, SwitchEventType eventType) {
        InfoData data = new SwitchInfoData()
                .withSwitchId(dpid.toString())
                .withState(eventType);
        return buildMessage(data);
    }

    private Message buildMessage(InfoData data) {
        return new InfoMessage()
                .withData(data)
                .withTimestamp(System.currentTimeMillis());
    }

    private Message buildPortMessage(DatapathId switchId, OFPort port, PortChangeType type) {
        InfoData data = new PortInfoData()
                .withSwitchId(switchId.toString())
                .withPortNo(port.getPortNumber())
                .withState(type);
        return(buildMessage(data));
    }

    private Message buildPortMessage(DatapathId switchId, OFPortDesc port, PortChangeType type) {
        InfoData data = new PortInfoData()
                .withSwitchId(switchId.toString())
                .withPortNo(port.getPortNo().getPortNumber())
                .withState(type);
        return (buildMessage(data));
    }

    /**
     * KafkaProducer
     */
    public class Producer implements Runnable {
        public Message dequeueItem() {
            if (!queue.isEmpty()) {
                logger.debug("Queue size: " + queue.size());
                return queue.remove();
            } else {
                return null;
            }
        }

        @Override
        public void run() {
            logger.debug("Running a Producer");
            KafkaProducer<String, String> producer = new KafkaProducer<>(kafkaProps);
            try {
                while (true) {
                    Message message = dequeueItem();
                    if (message != null) {
                        logger.debug("message = " + message.toJson());
                        producer.send(new ProducerRecord<>(topic, message.toJson()));
                    }
                    Thread.sleep(5);
                }
            } catch (Exception exception) {
                logger.error("Error: ", exception);
            }
            producer.close();
        }
    }
}
