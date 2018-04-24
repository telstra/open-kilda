package org.openkilda.wfm.topology.opentsdb.bolts;

import static org.openkilda.messaging.Utils.MAPPER;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.openkilda.messaging.info.Datapoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DatapointParseBolt extends BaseRichBolt {
    private static final Logger LOGGER = LoggerFactory.getLogger(DatapointParseBolt.class);
    private OutputCollector collector;

    @Override
    public void prepare(Map map, TopologyContext topologyContext, OutputCollector collector) {
        this.collector = collector;
    }

    @Override
    public void execute(Tuple tuple) {
        final String data = tuple.getString(0);
        LOGGER.debug("Processing datapoint: " + data);
        try {
            Datapoint datapoint = MAPPER.readValue(data, Datapoint.class);
            List<Object> stream = Stream.of(datapoint.simpleHashCode(), datapoint)
                    .collect(Collectors.toList());
            collector.emit(stream);
        } catch (Exception e) {
            LOGGER.error("Failed reading data: " + data, e);
        } finally {
            collector.ack(tuple);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields("hash", "datapoint"));
    }
}
