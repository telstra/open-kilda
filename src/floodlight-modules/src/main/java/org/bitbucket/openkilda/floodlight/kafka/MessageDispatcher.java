package org.bitbucket.openkilda.floodlight.kafka;

import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.kafka.common.errors.WakeupException;
import org.bitbucket.openkilda.floodlight.message.CommandMessage;
import org.bitbucket.openkilda.floodlight.message.Message;
import org.bitbucket.openkilda.floodlight.message.command.CommandData;
import org.bitbucket.openkilda.floodlight.message.command.DefaultFlowsCommandData;
import org.bitbucket.openkilda.floodlight.message.command.DiscoverISLCommandData;
import org.bitbucket.openkilda.floodlight.message.command.DiscoverPathCommandData;
import org.bitbucket.openkilda.floodlight.pathverification.IPathVerificationService;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

public class MessageDispatcher implements Runnable, IMessageDispatcher {
  private static final Logger logger = LoggerFactory.getLogger(MessageDispatcher.class);
  private final AtomicBoolean closed;
  private ConcurrentLinkedQueue<String> queue;
  private IPathVerificationService pathVerificationService;
  private ObjectMapper mapper;

  public MessageDispatcher(ConcurrentLinkedQueue<String> queue, IPathVerificationService pathVerificationService) {
    this.queue = queue;
    closed = new AtomicBoolean(false);
    mapper = new ObjectMapper();
    this.pathVerificationService = pathVerificationService;
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
  
  public void parseMessage(String json) {
    try {
      Message message = mapper.readValue(json, Message.class);
      if (message instanceof CommandMessage) {
        logger.debug("got a CommandMessage");
        CommandMessage cmdMsg = (CommandMessage) message;
        if (cmdMsg.getData().getDestination() == CommandData.Destination.CONTROLLER) {
          doControllerCommand(cmdMsg);
        }
      }
    } catch (IOException e) {
      logger.error("error parsing message", e);
    }
  }
  
  public void doControllerCommand(CommandMessage message) {
    CommandData data = message.getData();
    if (data instanceof DefaultFlowsCommandData) {
      logger.debug("adding default flows");
      logger.debug("pathVerificationService: " + pathVerificationService);
      DefaultFlowsCommandData command = (DefaultFlowsCommandData) data;
      DatapathId dpid = DatapathId.of(command.getSwitchId());
      pathVerificationService.installVerificationRule(dpid, true);
    } else if (data instanceof DiscoverISLCommandData) {
      DiscoverISLCommandData command = (DiscoverISLCommandData) data;
      logger.debug("starting ISL discovery on " + command.getSwitchId() + " - " + command.getPortNo());
      pathVerificationService.sendDiscoveryMessage(DatapathId.of(command.getSwitchId()), OFPort.of(command.getPortNo()));
    } else if (data instanceof DiscoverPathCommandData) {
      
    } else {
      logger.error("unsupport CommandData class " + data.getClass().getCanonicalName());
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
          parseMessage(message);
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
