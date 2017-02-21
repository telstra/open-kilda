package org.bitbucket.openkilda.floodlight.kafka;

import net.floodlightcontroller.core.module.IFloodlightService;

public interface IKafkaService extends IFloodlightService {
  public boolean topicExists(String queueName);
  public boolean createTopic(String queueName);
}
