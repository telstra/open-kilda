package org.bitbucket.openkilda.floodlight.kafka;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.bitbucket.openkilda.floodlight.type.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;

public class KafkaService implements IFloodlightModule, IKafkaService {
  protected IFloodlightProviderService floodlightProvider;
  private Logger logger;
  protected Properties kafkaProps;
  private Producer<String, String> producer;
  private static ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();
  

  /**
   * IKafkaServiceMethods
   */
  
  @Override
  public boolean postMessage(String topic, Message message) {
    try {
      producer.send(new ProducerRecord<String, String>(topic, message.toJson()));
    } catch (Exception e) {
      logger.error("Error converting to JSON.", e);
      return false;
    }
    return true;
  }

  @Override
  public boolean topicExists(String queueName) {
    
    return false;
  }

  @Override
  public boolean createTopic(String queueName) {
    return false;
  }

  /**
   * IFloodlightModule Methods
   */
  
  @Override
  public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
    Collection<Class<? extends IFloodlightService>> services = new ArrayList<>();
    services.add(IFloodlightProviderService.class);
    return services;
  }

  @Override
  public Collection<Class<? extends IFloodlightService>> getModuleServices() {
    Collection<Class<? extends IFloodlightService>> services = new ArrayList<>();
    services.add(IKafkaService.class);
    return services;
  }

  @Override
  public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
    Map<Class<? extends IFloodlightService>, IFloodlightService> map = new HashMap<>();
    map.put(IKafkaService.class, this);
    return map;
  }

  @Override
  public void init(FloodlightModuleContext context) throws FloodlightModuleException {
    floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
    logger = LoggerFactory.getLogger(KafkaService.class);
    
    Map<String, String> configParameters = context.getConfigParams(this);
    kafkaProps = new Properties();
    kafkaProps.put("bootstrap.servers", configParameters.get("bootstrap-servers"));
    kafkaProps.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
    kafkaProps.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
  }

  @Override
  public void startUp(FloodlightModuleContext arg0) throws FloodlightModuleException {
    logger.info("Starting " + KafkaService.class.getCanonicalName());
    
    // Start Threads
    ExecutorService executor = Executors.newFixedThreadPool(10);
    executor.execute(new KafkaListener(queue));
    executor.execute(new MessageDispatcher(queue));
    producer = new KafkaProducer<>(kafkaProps);
  }

}
