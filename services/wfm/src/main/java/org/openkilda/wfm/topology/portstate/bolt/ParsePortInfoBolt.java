package org.openkilda.wfm.topology.portstate.bolt;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Tuple;
import org.openkilda.messaging.Utils;
import org.openkilda.messaging.info.Datapoint;
import org.openkilda.messaging.info.event.PortChangeType;
import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.wfm.topology.AbstractTopology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ParsePortInfoBolt extends BaseRichBolt {
    private static final Logger logger = LoggerFactory.getLogger(ParsePortInfoBolt.class);
    private static final String METRIC_NAME = "pen.switch.state";
    private OutputCollector collector;
    private Table<String, Integer, Map<String, String>> tagsTable;

    @Override
    public void prepare(Map map, TopologyContext topologyContext, OutputCollector outputCollector) {
        this.collector = outputCollector;
        tagsTable = HashBasedTable.create();
    }

    @Override
    public void execute(Tuple tuple) {
        try {
            if (tuple.getValueByField(TopoDiscoParseBolt.FIELD_NAME) instanceof PortInfoData) {
                try {
                    List<Object> result = makeTsdbDatapoint(
                            (PortInfoData) tuple.getValueByField(TopoDiscoParseBolt.FIELD_NAME));
                    logger.debug("Emitting: {}", result);
                    collector.emit(result);
                } catch (IOException e) {
                    logger.error("Error creating tsdbDatapoint for: {}", tuple.toString(), e);
                }
            }
        } finally {
            collector.ack(tuple);
        }
    }

    private List<Object> makeTsdbDatapoint(PortInfoData data) throws IOException {
        return tsdbTuple(METRIC_NAME,
                data.getTimestamp(),
                getStateAsInt(data),
                getTags(data));
    }

    private int getStateAsInt(PortInfoData data) {
        return data.getState() == PortChangeType.UP ? 1 : 0;
    }

    private static List<Object> tsdbTuple(String metric, long timestamp, Number value, Map<String, String> tag)
            throws IOException {
        Datapoint datapoint = new Datapoint(metric, timestamp, tag, value);
        return Collections.singletonList(Utils.MAPPER.writeValueAsString(datapoint));
    }

    private Map<String, String> getTags(PortInfoData data) {
        Map<String, String> tag = tagsTable.get(data.getSwitchId(), data.getPortNo());
        if (tag == null) {
            tag = new HashMap<>();
            tag.put("switchid", data.getSwitchId().replaceAll(":", ""));
            tag.put("port", String.valueOf(data.getPortNo()));
            tagsTable.put(data.getSwitchId(), data.getPortNo(), tag);
        }
        return tag;
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(AbstractTopology.fieldMessage);
    }
}
