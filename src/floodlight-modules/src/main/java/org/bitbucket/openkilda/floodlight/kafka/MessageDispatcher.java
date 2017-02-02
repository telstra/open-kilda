package org.bitbucket.openkilda.floodlight.kafka;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.kafka.common.errors.WakeupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageDispatcher implements Runnable {
  private static final Logger logger = LoggerFactory.getLogger(MessageDispatcher.class);
  private final AtomicBoolean closed;
  private ConcurrentLinkedQueue<String> queue;

  public MessageDispatcher(ConcurrentLinkedQueue<String> queue) {
    this.queue = queue;
    closed = new AtomicBoolean(false);
  }

  public String dequeueItem() {
    if (!queue.isEmpty()) {
      logger.debug("Queue size: " + queue.size());
      return queue.remove();
    } else {
      return null;
    }
  }

  public int getQueueSize() {
    if (!queue.isEmpty()) {
      return queue.size();
    } else {
      return 0;
    }
  }

  @Override
  public void run() {
    logger.info("starting a MessageDispatcher");
    try {
      while (!closed.get()) {
        String message = dequeueItem();
        if (message != null) {
          logger.debug("message = " + message);
        }
        Thread.sleep(5);
      }
    } catch (WakeupException e) {
      if (!closed.get()) {
        throw e;
      }
    } catch (InterruptedException e) {
      logger.error("Error tyring to sleep", e);
    }
  }
}
