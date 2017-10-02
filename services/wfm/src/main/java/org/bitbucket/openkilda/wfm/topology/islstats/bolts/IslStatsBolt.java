package org.bitbucket.openkilda.wfm.topology.islstats.bolts;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.storm.Constants;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Tuple;
import org.bitbucket.openkilda.messaging.Message;
import org.bitbucket.openkilda.messaging.Utils;
import org.bitbucket.openkilda.messaging.info.InfoMessage;
import org.bitbucket.openkilda.messaging.info.event.IslInfoData;
import org.bitbucket.openkilda.messaging.info.event.PathNode;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.storm.utils.Utils.tuple;

public class IslStatsBolt extends BaseRichBolt {

    private OutputCollector collector;
    private static Logger logger = LogManager.getLogger(IslStatsBolt.class);

    @Override
    public void prepare(Map map, TopologyContext topologyContext, OutputCollector outputCollector) {
        this.collector = collector;
    }

    @Override
    public void execute(Tuple tuple) {
        // Do we need to verify coming from Spout?
        String json = tuple.getString(0);
        try {
            Message stats = Utils.MAPPER.readValue(json, Message.class);
            if (!(stats instanceof InfoMessage)) {
                collector.ack(tuple);
                return;
            }
            InfoMessage message = (InfoMessage) stats;
            // TODO:  Probably need to verify that this is a IslInfoData?
            IslInfoData data = (IslInfoData) message.getData();
            List<PathNode> path = data.getPath();

            // Build Opentsdb entry
            Map<String, String> tags = new HashMap<>();
            tags.put("src_switch", path.get(0).getSwitchId().replaceAll(":", ""));
            tags.put("src_port", String.valueOf(path.get(0).getPortNo()));
            tags.put("dst_switch", path.get(1).getSwitchId().replaceAll(":", ""));
            tags.put("dst_port", String.valueOf(path.get(1).getPortNo()));
            long timestamp = message.getTimestamp();
            collector.emit(tuple("pen.isl.latency", timestamp, data.getLatency(), tags));

        } catch(IOException e) {
            logger.warn("EXCEPTION during JSON parsing: {}, error: {}", json, e.getMessage());
            e.printStackTrace();
        } finally {
            collector.ack(tuple);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {

    }

    protected static boolean isTickTuple(Tuple tuple) {
        return tuple.getSourceComponent().equals(Constants.SYSTEM_COMPONENT_ID)
                && tuple.getSourceStreamId().equals(Constants.SYSTEM_TICK_STREAM_ID);
    }
}
