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
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

public class IslStatsBolt extends BaseRichBolt {

    private OutputCollector collector;
    private static final Logger logger = LogManager.getLogger(IslStatsBolt.class);

    private static final Fields DEFAULT_METRIC_FIELDS =
            new Fields(TupleOpenTsdbDatapointMapper.DEFAULT_MAPPER.getMetricField(),
                    TupleOpenTsdbDatapointMapper.DEFAULT_MAPPER.getTimestampField(),
                    TupleOpenTsdbDatapointMapper.DEFAULT_MAPPER.getValueField(),
                    TupleOpenTsdbDatapointMapper.DEFAULT_MAPPER.getTagsField());

    private static List<Object> tsdbTuple(Object metric, Object timestamp, Number value, Map<String, String> tag) {
        return Stream.of(metric, timestamp, value, tag).collect(toList());
    }

    @Override
    public void prepare(Map map, TopologyContext topologyContext, OutputCollector collector) {
        this.collector = collector;
    }

    public List<Object> buildTsdbTuple(IslInfoData data, long timestamp) {
        List<PathNode> path = data.getPath();
        Map<String, String> tags = new HashMap<>();
        tags.put("src_switch", path.get(0).getSwitchId().replaceAll(":", ""));
        tags.put("src_port", String.valueOf(path.get(0).getPortNo()));
        tags.put("dst_switch", path.get(1).getSwitchId().replaceAll(":", ""));
        tags.put("dst_port", String.valueOf(path.get(1).getPortNo()));

        return tsdbTuple("pen.isl.latency", timestamp, data.getLatency(), tags);
    }

    private String getJson(Tuple tuple) {
        return tuple.getString(0);
    }

    private Message getMessage(String json) throws IOException {
        return Utils.MAPPER.readValue(json, Message.class);
    }

    public InfoData getInfoData(Message message) throws Exception {
        if (!(message instanceof InfoMessage)) {
            throw new Exception(message.getClass().getName() + " is not an InfoMessage");
        }
        InfoData data = ((InfoMessage) message).getData();
        return data;
    }

    public IslInfoData getIslInfoData(InfoData data) throws Exception {
        if (!(data instanceof IslInfoData)) {
            throw new Exception(data.getClass().getName() + " is not an IslInfoData");
        }
        return (IslInfoData) data;
    }

    @Override
    public void execute(Tuple tuple) {
        logger.debug("tuple: " + tuple);
        String json = getJson(tuple);
        try {
            Message message = getMessage(json);
            IslInfoData data = getIslInfoData(getInfoData(message));
            List<Object> results = buildTsdbTuple(data, message.getTimestamp());
            logger.debug("emit: " + results);
            collector.emit(results);
        } catch(IOException e) {
            logger.error("Could not deserialize message={}", json, e);
        } catch(Exception e) {
            // TODO: has to be a cleaner way to do this?
        } finally {
            collector.ack(tuple);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(DEFAULT_METRIC_FIELDS);
    }

}
