/* Copyright 2017 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.wfm.topology.islstats.bolts;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.storm.Constants;
import org.apache.storm.opentsdb.bolt.TupleOpenTsdbDatapointMapper;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.Utils;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.PathNode;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.storm.utils.Utils.tuple;

public class IslStatsBolt extends BaseRichBolt {

    private OutputCollector collector;
    private static Logger logger = LogManager.getLogger(IslStatsBolt.class);

    private static final Fields DEFAULT_METRIC_FIELDS =
            new Fields(TupleOpenTsdbDatapointMapper.DEFAULT_MAPPER.getMetricField(),
                    TupleOpenTsdbDatapointMapper.DEFAULT_MAPPER.getTimestampField(),
                    TupleOpenTsdbDatapointMapper.DEFAULT_MAPPER.getValueField(),
                    TupleOpenTsdbDatapointMapper.DEFAULT_MAPPER.getTagsField());

    @Override
    public void prepare(Map map, TopologyContext topologyContext, OutputCollector collector) {
        this.collector = collector;
    }

    @Override
    public void execute(Tuple tuple) {
        String json = tuple.getString(0);
        try {
            Message message = Utils.MAPPER.readValue(json, Message.class);
            if (!(message instanceof InfoMessage)) {
                collector.ack(tuple);
                return;
            }
            long timestamp = message.getTimestamp();
            InfoData infoData = ((InfoMessage) message).getData();
            if (!(infoData instanceof IslInfoData)) {
                collector.ack(tuple);
                return;
            }
            IslInfoData data = (IslInfoData) infoData;
            List<PathNode> path = data.getPath();

            // Build Opentsdb entry
            Map<String, String> tags = new HashMap<>();
            tags.put("src_switch", path.get(0).getSwitchId().replaceAll(":", ""));
            tags.put("src_port", String.valueOf(path.get(0).getPortNo()));
            tags.put("dst_switch", path.get(1).getSwitchId().replaceAll(":", ""));
            tags.put("dst_port", String.valueOf(path.get(1).getPortNo()));
            collector.emit(tuple("pen.isl.latency", timestamp, data.getLatency(), tags));

        } catch(IOException e) {
            logger.error("Could not deserialize message={}", json, e);
        } finally {
            collector.ack(tuple);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(DEFAULT_METRIC_FIELDS);
    }

    protected static boolean isTickTuple(Tuple tuple) {
        return tuple.getSourceComponent().equals(Constants.SYSTEM_COMPONENT_ID)
                && tuple.getSourceStreamId().equals(Constants.SYSTEM_TICK_STREAM_ID);
    }
}
