package org.bitbucket.openkilda.floodlight.switchmanager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import org.bitbucket.openkilda.floodlight.kafka.IKafkaService;
import org.bitbucket.openkilda.floodlight.message.InfoMessage;
import org.bitbucket.openkilda.floodlight.message.Message;
import org.bitbucket.openkilda.floodlight.message.MessageData;
import org.bitbucket.openkilda.floodlight.message.info.InfoData;
import org.bitbucket.openkilda.floodlight.message.info.PortInfoData;
import org.bitbucket.openkilda.floodlight.message.info.SwitchInfoData;
import org.bitbucket.openkilda.floodlight.message.info.SwitchInfoData.SwitchEventType;
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
      logger.info("activated" + mapper.writeValueAsString(message));
      kafkaService.postMessage(topic, message);
    } catch (JsonProcessingException e) {
      logger.error(e.toString());
    }
  }

  @Override
  public void switchAdded(DatapathId dpid) {
    Message message = buildSwitchMessage(dpid, SwitchEventType.ADDED);
    try {
      logger.info("added" + mapper.writeValueAsString(message));
      kafkaService.postMessage(topic, message);
    } catch (JsonProcessingException e) {
      logger.error(e.toString());
    }
  }

  @Override
  public void switchChanged(DatapathId dpid) {
    Message message = buildSwitchMessage(dpid, SwitchEventType.CHANGED);
    try {
      logger.info("changed" + mapper.writeValueAsString(message));
      kafkaService.postMessage(topic, message);
    } catch (JsonProcessingException e) {
      logger.error(e.toString());
    }
  }

  @Override
  public void switchDeactivated(DatapathId dpid) {
    Message message = buildSwitchMessage(dpid, SwitchEventType.DEACTIVATED);
    try {
      logger.info("deactivated" + mapper.writeValueAsString(message));
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
    InfoData data = new PortInfoData()
        .withSwitchId(dpid.toString())
        .withPortNo(port.getPortNo().getPortNumber())
        .withState(changeType);
    try {
      logger.info(mapper.writeValueAsString(data));
      kafkaService.postMessage(topic, buildMessage(data));
    } catch (JsonProcessingException e) {
      logger.error(e.toString());
    }
  }
  
  /**
   * Utility functions
   */
  
  public Message buildSwitchMessage(DatapathId dpid, SwitchEventType eventType) {
    InfoData data = new SwitchInfoData()
        .withSwitchId(dpid.toString())
        .withState(eventType);
    return buildMessage(data);
  }
  
  public Message buildMessage(InfoData data) {
    return new InfoMessage()
        .withData(data)
        .withTimestamp(System.currentTimeMillis());
  }
}
