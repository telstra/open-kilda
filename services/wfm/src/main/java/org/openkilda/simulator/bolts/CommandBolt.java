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
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.discovery.DiscoverIslCommandData;
import org.openkilda.messaging.command.discovery.DiscoverPathCommandData;
import org.openkilda.messaging.command.flow.*;
import org.openkilda.simulator.SimulatorTopology;
import org.openkilda.simulator.classes.Commands;
import org.openkilda.wfm.OFEMessageUtils;

import java.util.List;
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

    protected void processCommand(Tuple tuple) throws Exception {
        CommandMessage command = Utils.MAPPER.readValue(getJson(tuple), CommandMessage.class);
        if (command.getDestination() == Destination.CONTROLLER) {
            CommandData data = command.getData();
            Commands switchCommand;
            String sw;
            if (data instanceof DiscoverIslCommandData) {
                switchCommand = Commands.DO_DISCOVER_ISL_COMMAND;
                sw = ((DiscoverIslCommandData) data).getSwitchId();
            } else if (data instanceof DiscoverPathCommandData) {
                switchCommand = Commands.DO_DISCOVER_PATH_COMMAND;
                sw = ((DiscoverPathCommandData) data).getSrcSwitchId();
            } else if (data instanceof InstallIngressFlow) {
                switchCommand = Commands.DO_INSTALL_INGRESS_FLOW;
                sw = ((InstallIngressFlow) data).getSwitchId();
            } else if (data instanceof InstallEgressFlow) {
                switchCommand = Commands.DO_INSTALL_EGRESS_FLOW;
                sw = ((InstallEgressFlow) data).getSwitchId();
            } else if (data instanceof InstallTransitFlow) {
                switchCommand = Commands.DO_INSTALL_TRANSIT_FLOW;
                sw = ((InstallTransitFlow) data).getSwitchId();
            } else if (data instanceof InstallOneSwitchFlow) {
                switchCommand = Commands.DO_INSTALL_ONESWITCH_FLOW;
                sw = ((InstallOneSwitchFlow) data).getSwitchId();
            } else if (data instanceof RemoveFlow) {
                switchCommand = Commands.DO_DELETE_FLOW;
                sw = ((RemoveFlow) data).getSwitchId();
            } else {
                logger.error("unknown data type: {}", data.toString());
                throw new Exception("Unknown command {}".format(data.getClass().getSimpleName()));
            }
            List<Integer> taskIDs = collector.emit(SimulatorTopology.COMMAND_BOLT_STREAM, tuple,
                    new Values(sw.toLowerCase(), switchCommand.name(), command.getData()));
            logger.info("{}:  {} - {}", switchCommand.name(), sw, command.getData().toString());
        }
    }

    @Override
    public void execute(Tuple tuple) {
        logger.debug("got tuple: {}", tuple.toString());
        try {
            String json = getJson(tuple);
            switch (getType(json)) {
                case "command":
                    processCommand(tuple);
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            logger.error("Could not parse tuple: {}".format(tuple.toString()), e);
        } finally {
            collector.ack(tuple);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        outputFieldsDeclarer.declareStream(SimulatorTopology.COMMAND_BOLT_STREAM,
                new Fields("dpid", SwitchBolt.TupleFields.COMMAND.name(), SwitchBolt.TupleFields.DATA.name()));
    }
}
