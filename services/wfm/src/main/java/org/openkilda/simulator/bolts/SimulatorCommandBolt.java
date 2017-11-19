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
import org.openkilda.messaging.Utils;
import org.openkilda.simulator.SimulatorTopology;
import org.openkilda.simulator.classes.SimulatorCommands;
import org.openkilda.simulator.messages.SwitchMessage;
import org.openkilda.simulator.messages.simulator.SimulatorMessage;
import org.openkilda.simulator.messages.simulator.command.PortModMessage;
import org.openkilda.simulator.messages.simulator.command.SwitchModMessage;
import org.openkilda.simulator.messages.simulator.command.TopologyMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SimulatorCommandBolt extends BaseRichBolt {
    private static final Logger logger = LogManager.getLogger(SimulatorCommandBolt.class);
    private OutputCollector collector;

    protected String getJson(Tuple tuple) {
        return tuple.getString(0);
    }

    private void doCommand(Tuple tuple) throws Exception {
        SimulatorMessage message = Utils.MAPPER.readValue(getJson(tuple), SimulatorMessage.class);
        List<Values> values = new ArrayList<>();
        String stream = "";
        if (message instanceof SwitchModMessage) {
            Values value = new Values(((SwitchModMessage) message).getDpid(), message, SimulatorCommands.DO_SWITCH_MOD);
            values.add(value);
            stream = SimulatorTopology.SIMULATOR_COMMAND_STREAM;
        } else if (message instanceof PortModMessage) {
            Values value = new Values(((PortModMessage) message).getDpid(), message, SimulatorCommands.DO_PORT_MOD);
            values.add(value);
            stream = SimulatorTopology.SIMULATOR_COMMAND_STREAM;
        } else if (message instanceof TopologyMessage) {
            stream = SimulatorTopology.SIMULATOR_COMMAND_STREAM;
            values = createTopology((TopologyMessage) message);
        } else {
            logger.error("Unknown simulator command received: {}\n{}",
                    message.getClass().getName(), tuple.toString());
        }

        if (values.size() > 0) {
            emit(stream, tuple, values);
        }
    }

    private List<Integer> emit(String stream, Tuple tuple, List<Values> values) throws Exception {
        List<Integer> workers = null;
        if (values.size() > 0) {
            for (Values value : values) {
                workers = collector.emit(stream, tuple, value);
            }
        }
        return workers;
    }

    private List<Values> createTopology(TopologyMessage topology) {
        List<SwitchMessage> switches = topology.getSwitches();
        List<Values> values = new ArrayList<>();
        for (SwitchMessage sw : switches) {
            // Need to pre-pend 00:00 as DPID as returned by OpenflowJ has 8 octets
            values.add(new Values(String.format("00:00:%s",sw.getDpid()).toLowerCase(), sw,
                    SimulatorCommands.DO_ADD_SWITCH));
            logger.info("Add Switch: {}", sw.getDpid());
        }
        return values;
    }

    @Override
    public void prepare(Map map, TopologyContext topologyContext, OutputCollector outputCollector) {
        this.collector = outputCollector;
    }

    @Override
    public void execute(Tuple tuple) {
        try {
            String tupleSource = tuple.getSourceComponent();
            switch (tupleSource) {
                case SimulatorTopology.SIMULATOR_SPOUT:
                    doCommand(tuple);
                    break;
                default:
                    logger.error("received command from unknown source: {}", tupleSource);
            }
        } catch (Exception e) {
            logger.error(e);
        } finally {
            collector.ack(tuple);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
//        outputFieldsDeclarer.declareStream(SimulatorTopology.SIMULATOR_COMMAND_STREAM,
//                new Fields("dpid", "data"));
        outputFieldsDeclarer.declareStream(SimulatorTopology.SIMULATOR_COMMAND_STREAM,
                new Fields("dpid", "data", "command"));
    }
}
