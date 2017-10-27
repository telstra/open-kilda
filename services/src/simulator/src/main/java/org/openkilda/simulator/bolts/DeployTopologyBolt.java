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
import org.openkilda.simulator.messages.SwitchMessage;
import org.openkilda.simulator.messages.TopologyMessage;

import java.util.List;
import java.util.Map;

public class DeployTopologyBolt extends BaseRichBolt {
    private static final Logger logger = LogManager.getLogger(CommandBolt.class);
    private OutputCollector collector;

    @Override
    public void prepare(Map map, TopologyContext topologyContext, OutputCollector outputCollector) {
        this.collector = outputCollector;
    }

    protected void createTopology(Tuple tuple, TopologyMessage topology) {
        List<SwitchMessage> switches = topology.getSwitches();
        switches.forEach(sw -> {
            collector.emit(tuple, new Values(sw.getDpid(), sw));
        });
    }

    @Override
    public void execute(Tuple tuple) {
        logger.debug("received tuple: " + tuple);
        Fields fields = tuple.getFields();
        if (fields.contains("topology")) {
            TopologyMessage message = (TopologyMessage) tuple.getValueByField("topology");
            createTopology(tuple, message);
        }
        collector.ack(tuple);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        outputFieldsDeclarer.declare(new Fields("dpid", "switch"));
    }
}
