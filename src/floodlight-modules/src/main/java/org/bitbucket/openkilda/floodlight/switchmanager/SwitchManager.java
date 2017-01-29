package org.bitbucket.openkilda.floodlight.switchmanager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import org.bitbucket.openkilda.floodlight.kafka.IKafkaService;
import org.bitbucket.openkilda.floodlight.switchmanager.type.PortMessageData;
import org.bitbucket.openkilda.floodlight.switchmanager.type.SwitchEventType;
import org.bitbucket.openkilda.floodlight.switchmanager.type.SwitchMessageData;
import org.bitbucket.openkilda.floodlight.type.Message;
import org.bitbucket.openkilda.floodlight.type.MessageData;
import org.bitbucket.openkilda.floodlight.type.MessageType;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.types.DatapathId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.PortChangeType;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;

public class SwitchManager implements IFloodlightModule, IOFSwitchListener {
  protected Logger logger;
  protected IFloodlightProviderService floodlightProvider;
  protected IOFSwitchService switchService; 
  protected IKafkaService kafkaService;
  protected String topic;
  private ObjectMapper mapper;

  /**
   * IFloodlightModule Methods
   */
  
  @Override
  public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
    Collection<Class<? extends IFloodlightService>> services = new ArrayList<>();
    services.add(IFloodlightProviderService.class);
    services.add(IOFSwitchService.class);
    services.add(IKafkaService.class);
    return services;
  }

  @Override
  public Collection<Class<? extends IFloodlightService>> getModuleServices() {
    return null;
  }

  @Override
  public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
    return null;
  }

  @Override
  public void init(FloodlightModuleContext context) throws FloodlightModuleException {
    floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
    switchService = context.getServiceImpl(IOFSwitchService.class);
    kafkaService = context.getServiceImpl(IKafkaService.class);
    logger = LoggerFactory.getLogger(SwitchManager.class);
    mapper = new ObjectMapper();
    Map<String, String> configParameters = context.getConfigParams(this);
    topic = configParameters.get("topic");
  }

  @Override
  public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
    logger.info("Starting " + SwitchManager.class.getCanonicalName());
    switchService.addOFSwitchListener(this);
  }

  /**
   * IOFSwitchListener Methods
   */
  
  @Override
  public void switchActivated(DatapathId dpid) {
    Message message = buildSwitchMessage(dpid, SwitchEventType.ACTIVATED);
    try {
      logger.info(mapper.writeValueAsString(message));
      kafkaService.postMessage(topic, message);
    } catch (JsonProcessingException e) {
      logger.error(e.toString());
    }
  }

  @Override
  public void switchAdded(DatapathId dpid) {
    Message message = buildSwitchMessage(dpid, SwitchEventType.ADDED);
    try {
      logger.info(mapper.writeValueAsString(message));
      kafkaService.postMessage(topic, message);
    } catch (JsonProcessingException e) {
      logger.error(e.toString());
    }
  }

  @Override
  public void switchChanged(DatapathId dpid) {
    Message message = buildSwitchMessage(dpid, SwitchEventType.CHANGED);
    try {
      logger.info(mapper.writeValueAsString(message));
      kafkaService.postMessage(topic, message);
    } catch (JsonProcessingException e) {
      logger.error(e.toString());
    }
  }

  @Override
  public void switchDeactivated(DatapathId dpid) {
    Message message = buildSwitchMessage(dpid, SwitchEventType.DEACTIVATED);
    try {
      logger.info(mapper.writeValueAsString(message));
      kafkaService.postMessage(topic, message);
    } catch (JsonProcessingException e) {
      logger.error(e.toString());
    }
  }
  
  @Override
  public void switchRemoved(DatapathId dpid) {
    Message message = buildSwitchMessage(dpid, SwitchEventType.REMOVED);
    try {
      logger.info(mapper.writeValueAsString(message));
      kafkaService.postMessage(topic, message);
    } catch (JsonProcessingException e) {
      logger.error(e.toString());
    }
  }

  @Override
  public void switchPortChanged(DatapathId dpid, OFPortDesc port, PortChangeType changeType) {
    PortMessageData data = new PortMessageData()
        .withEventType(changeType)
        .withSwitchId(dpid.toString())
        .withPortNo(port.getPortNo().getPortNumber());
    Message message = buildMessage(MessageType.PORT, data);
    try {
      logger.info(mapper.writeValueAsString(message));
    } catch (JsonProcessingException e) {
      logger.error(e.toString());
    }
  }
  
  /**
   * Utility functions
   */
  
  public Message buildSwitchMessage(DatapathId dpid, SwitchEventType eventType) {
    SwitchMessageData data = new SwitchMessageData()
        .withEventType(eventType)
        .withSwitchId(dpid.toString());
    
    return buildMessage(MessageType.SWITCH, data);
  }
  
  public Message buildMessage(MessageType type, MessageData data) {
    return new Message()
        .withMessageType(type)
        .withTimestamp(System.currentTimeMillis())
        .withController(floodlightProvider.getControllerId())
        .withData(data);
  }
}
