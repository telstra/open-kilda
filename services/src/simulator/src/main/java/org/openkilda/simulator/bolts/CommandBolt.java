package org.openkilda.simulator.bolts;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.openkilda.messaging.Destination;
import org.openkilda.messaging.Utils;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.simulator.SimulatorTopology;
import org.openkilda.simulator.messages.TopologyMessage;
import org.openkilda.wfm.OFEMessageUtils;

import java.util.Map;

public class CommandBolt extends BaseRichBolt {
    private static final Logger logger = LogManager.getLogger(CommandBolt.class);
    private OutputCollector collector;

    @Override
    public void prepare(Map map, TopologyContext topologyContext, OutputCollector outputCollector) {
        this.collector = outputCollector;
    }

    protected String getType(String json) throws Exception {
        try {
            Map<String, ?> root = OFEMessageUtils.fromJson(json);
            return ((String) root.get("type")).toLowerCase();
        } catch (Exception e) {
            logger.error("error getting type in: {}", json);
            throw e;
        }
    }

    protected String getJson(Tuple tuple) {
        return tuple.getString(0);
    }

    protected void processCommand(Tuple tuple, String json) throws Exception {
        CommandMessage command = Utils.MAPPER.readValue(json, CommandMessage.class);
        if (command.getDestination() == Destination.CONTROLLER) {
            collector.emit(SimulatorTopology.COMMAND_BOLT_STREAM, tuple, new Values(command.getData()));

        }
    }

    @Override
    public void execute(Tuple tuple) {
        try {
            String json = getJson(tuple);
            switch (getType(json)) {
                case "topology":
                    TopologyMessage message = Utils.MAPPER.readValue(json, TopologyMessage.class);
                    logger.debug("emitting: " + message.toString());
                    collector.emit(SimulatorTopology.DEPLOY_TOPOLOGY_BOLT_STREAM, tuple, new Values(message));
                    break;
                case "command":
                    processCommand(tuple, json);
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            logger.error("Could not parse: ", e);
        } finally {
            collector.ack(tuple);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        outputFieldsDeclarer.declareStream(SimulatorTopology.DEPLOY_TOPOLOGY_BOLT_STREAM, new Fields("topology"));
        outputFieldsDeclarer.declareStream(SimulatorTopology.COMMAND_BOLT_STREAM, new Fields("command"));
    }
}
