package org.bitbucket.openkilda.floodlight.switchmanager;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.PortChangeType;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.bitbucket.openkilda.floodlight.kafka.KafkaMessageProducer;
import org.bitbucket.openkilda.messaging.Message;
import org.bitbucket.openkilda.messaging.info.InfoData;
import org.bitbucket.openkilda.messaging.info.InfoMessage;
import org.bitbucket.openkilda.messaging.info.event.PortInfoData;
import org.bitbucket.openkilda.messaging.info.event.SwitchEventType;
import org.bitbucket.openkilda.messaging.info.event.SwitchInfoData;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.types.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class SwitchEventCollector implements IFloodlightModule, IOFSwitchListener, IFloodlightService {

    private IOFSwitchService switchService;
    private Logger logger;
    private Properties kafkaProps;
    private String topic;
    private KafkaMessageProducer kafkaProducer;
    private ISwitchManager switchManager;
    private ObjectMapper mapper = new ObjectMapper();

    /**
     * IOFSwitchListener methods
     */

    @Override
    public void switchAdded(final DatapathId switchId) {
        Message message = buildSwitchMessage(switchId, SwitchEventType.ADDED);
        postMessage(topic, message);
    }

    @Override
    public void switchRemoved(final DatapathId switchId) {
        Message message = buildSwitchMessage(switchId, SwitchEventType.REMOVED);
        postMessage(topic, message);
    }

    @Override
    public void switchActivated(final DatapathId switchId) {
        Message message = buildSwitchMessage(switchId, SwitchEventType.ACTIVATED);
        switchManager.installDefaultRules(switchId);
        postMessage(topic, message);

        IOFSwitch sw = switchService.getSwitch(switchId);
        if (sw.getEnabledPortNumbers() != null) {
            for (OFPort p : sw.getEnabledPortNumbers()) {
                postMessage(topic, buildPortMessage(sw.getId(), p, PortChangeType.UP));
            }
        }
    }

    @Override
    public void switchPortChanged(final DatapathId switchId, final OFPortDesc port, final PortChangeType type) {
        Message message = buildPortMessage(switchId, port, type);
        postMessage(topic, message);
    }

    @Override
    public void switchChanged(final DatapathId switchId) {
        Message message = buildSwitchMessage(switchId, SwitchEventType.CHANGED);
        postMessage(topic, message);
    }

    @Override
    public void switchDeactivated(final DatapathId switchId) {
        Message message = buildSwitchMessage(switchId, SwitchEventType.DEACTIVATED);
        postMessage(topic, message);
    }

    /*
     * IFloodlightModule methods.
     */

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        Collection<Class<? extends IFloodlightService>> services = new ArrayList<>();
        services.add(SwitchEventCollector.class);
        return services;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        Map<Class<? extends IFloodlightService>, IFloodlightService> map = new HashMap<>();
        map.put(SwitchEventCollector.class, this);
        return map;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> services = new ArrayList<>();
        services.add(IFloodlightProviderService.class);
        services.add(IOFSwitchService.class);
        services.add(KafkaMessageProducer.class);
        services.add(ISwitchManager.class);
        return services;
    }

    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        IFloodlightProviderService floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
        switchService = context.getServiceImpl(IOFSwitchService.class);
        kafkaProducer = context.getServiceImpl(KafkaMessageProducer.class);
        switchManager = context.getServiceImpl(ISwitchManager.class);
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

    /*
     * Utility functions
     */

    /**
     * buildSwitchMessage - Builds a switch message type.
     *
     * @param dpid - switch id
     * @param eventType - type of event
     * @return Message
     */
    private Message buildSwitchMessage(final DatapathId dpid, final SwitchEventType eventType) {
        InfoData data = new SwitchInfoData(dpid.toString(), eventType);
        return buildMessage(data);
    }

    /**
     * buildMessage - Builds a generic message object.
     *
     * @param data - data to use in the message body
     * @return Message
     */
    private Message buildMessage(final InfoData data) {
        return new InfoMessage(data, System.currentTimeMillis(), "system");
    }

    private static org.bitbucket.openkilda.messaging.info.event.PortChangeType toJsonType(PortChangeType type) {
        switch (type) {
            case ADD:
                return org.bitbucket.openkilda.messaging.info.event.PortChangeType.ADD;
            case OTHER_UPDATE:
                return org.bitbucket.openkilda.messaging.info.event.PortChangeType.OTHER_UPDATE;
            case DELETE:
                return org.bitbucket.openkilda.messaging.info.event.PortChangeType.DELETE;
            case UP:
                return org.bitbucket.openkilda.messaging.info.event.PortChangeType.UP;
            default:
                return org.bitbucket.openkilda.messaging.info.event.PortChangeType.DOWN;
        }
    }

    /**
     * buildPortMessage - Builds a port state change message with port number.
     *
     * @param switchId - datapathId of switch
     * @param port - port that triggered the event
     * @param type - type of port event
     * @return Message
     */
    private Message buildPortMessage(final DatapathId switchId, final OFPort port, final PortChangeType type) {
        InfoData data = new PortInfoData(switchId.toString(), port.getPortNumber(), toJsonType(type));
        return buildMessage(data);
    }

    /**
     * buildPortMessage - Builds a port state message with OFPortDesc.
     *
     * @param switchId - datapathId of switch
     * @param port - port that triggered the event
     * @param type - type of port event
     * @return Message
     */
    private Message buildPortMessage(final DatapathId switchId, final OFPortDesc port, final PortChangeType type) {
        InfoData data = new PortInfoData(switchId.toString(), port.getPortNo().getPortNumber(), toJsonType(type));
        return buildMessage(data);
    }

    /**
     * postMessage - Send the message to Kafka.
     *
     * @param topic - topic to post the message to
     * @param message - message to pose
     */
    private void postMessage(final String topic, final Message message) {
        try {
            kafkaProducer.send(new ProducerRecord<>(topic, mapper.writeValueAsString(message)));
        } catch (JsonProcessingException e) {
            logger.error("error", e);
        }
    }
}
