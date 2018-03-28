package org.openkilda.wfm.topology.portstate.spout;

import org.apache.storm.spout.SpoutOutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichSpout;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Values;
import org.openkilda.messaging.Destination;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.discovery.PortsCommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

public class SwitchPortsSpout extends BaseRichSpout {
    private static final Logger logger = LoggerFactory.getLogger(SwitchPortsSpout.class);
    private static final String CRON_TUPLE = "cron.tuple";
    private SpoutOutputCollector collector;
    private final String REQUESTER = this.getClass().getSimpleName();
    private static final int DEFAULT_FREQUENCY = 600;
    private final int frequency;

    public SwitchPortsSpout() {
        this(DEFAULT_FREQUENCY);
    }

    public SwitchPortsSpout(int frequency) {
        this.frequency = frequency;
    }

    @Override
    public void open(Map map, TopologyContext context, SpoutOutputCollector collector) {
        this.collector = collector;
    }

    @Override
    public void nextTuple() {
        Message message = buildPortsCommand(REQUESTER);
        logger.debug("emitting portsCommand: {}", message.toString());
        collector.emit(new Values(message));  // Note that no tupleId which means this is an untracked tuple which is
        try {                                 // required for the sleep
            Thread.sleep(frequency * 1000);
        } catch (InterruptedException e) {
            logger.error("Error sleeping");
        }
    }

    private Message buildPortsCommand(String requester) {
        return new CommandMessage(new PortsCommandData(requester), now(), uuid(), Destination.CONTROLLER);
    }

    private static String uuid() {
        return UUID.randomUUID().toString();
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields(CRON_TUPLE));
    }
}
