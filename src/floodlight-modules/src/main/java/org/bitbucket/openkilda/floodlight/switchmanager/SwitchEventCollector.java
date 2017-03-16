package org.bitbucket.openkilda.floodlight.switchmanager;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import org.bitbucket.openkilda.floodlight.kafka.KafkaMessageProducer;
import org.bitbucket.openkilda.floodlight.message.InfoMessage;
import org.bitbucket.openkilda.floodlight.message.Message;
import org.bitbucket.openkilda.floodlight.message.info.InfoData;
import org.bitbucket.openkilda.floodlight.message.info.PortInfoData;
import org.bitbucket.openkilda.floodlight.message.info.SwitchInfoData;
import org.bitbucket.openkilda.floodlight.message.info.SwitchInfoData.SwitchEventType;
import org.bitbucket.openkilda.floodlight.pathverification.IPathVerificationService;
import org.bitbucket.openkilda.floodlight.pathverification.PathVerificationService;
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
    private Properties kafkaProps;
    private String topic;
    private KafkaMessageProducer kafkaProducer;
    private IPathVerificationService pathVerificationService;

    /**
     * IOFSwitchListener methods
     */

    @Override
    public void switchAdded(DatapathId switchId) {
        Message message = buildSwitchMessage(switchId, SwitchEventType.ADDED);
        postMessage(topic, message);
    }

    @Override
    public void switchRemoved(DatapathId switchId) {
        Message message = buildSwitchMessage(switchId, SwitchEventType.REMOVED);
        postMessage(topic, message);
    }

    @Override
    public void switchActivated(DatapathId switchId) {
        Message message = buildSwitchMessage(switchId, SwitchEventType.ACTIVATED);
        pathVerificationService.installVerificationRules(switchId);
        postMessage(topic, message);

        IOFSwitch sw = switchService.getSwitch(switchId);
        if (sw.getEnabledPortNumbers() != null) {
            for (OFPort p : sw.getEnabledPortNumbers()) {
                postMessage(topic, buildPortMessage(sw.getId(), p, PortChangeType.UP));
            }
        }
    }

    @Override
    public void switchPortChanged(DatapathId switchId, OFPortDesc port, PortChangeType type) {
        Message message = buildPortMessage(switchId, port, type);
        postMessage(topic, message);
    }

    @Override
    public void switchChanged(DatapathId switchId) {
        Message message = buildSwitchMessage(switchId, SwitchEventType.CHANGED);
        postMessage(topic, message);
    }

    @Override
    public void switchDeactivated(DatapathId switchId) {
        Message message = buildSwitchMessage(switchId, SwitchEventType.DEACTIVATED);
        postMessage(topic, message);
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
        services.add(KafkaMessageProducer.class);
        services.add(IPathVerificationService.class);
        return services;
    }

    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        IFloodlightProviderService floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
        switchService = context.getServiceImpl(IOFSwitchService.class);
        kafkaProducer = context.getServiceImpl(KafkaMessageProducer.class);
        pathVerificationService = context.getServiceImpl(IPathVerificationService.class);
        logger = LoggerFactory.getLogger(SwitchEventCollector.class);

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

    private void postMessage(String topic, Message message) {
        try {
            kafkaProducer.send(new ProducerRecord<String, String>(topic, message.toJson()));
        } catch (JsonProcessingException e) {
            logger.error("error", e);
        }
    }
}
