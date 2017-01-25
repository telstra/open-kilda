package org.bitbucket.openkilda.floodlight.kafka;

import org.bitbucket.openkilda.floodlight.type.Message;

import net.floodlightcontroller.core.module.IFloodlightService;

public interface IKafkaService extends IFloodlightService {
  public boolean postMessage(String topic, Message message);
  public boolean topicExists(String queueName);
  public boolean createTopic(String queueName);
}
