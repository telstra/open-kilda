package org.bitbucket.openkilda.floodlight.kafka;


public interface IMessageDispatcher {
  public String dequeueItem();
  public int getQueueSize();
}
