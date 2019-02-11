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

import org.openkilda.messaging.Message;
import org.openkilda.messaging.Utils;
import org.openkilda.messaging.info.Datapoint;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.wfm.share.utils.MetricFormatter;
import org.openkilda.wfm.topology.AbstractTopology;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IslStatsBolt extends BaseRichBolt {
    private static final Logger logger = LoggerFactory.getLogger(IslStatsBolt.class);

    private transient OutputCollector collector;
    private MetricFormatter metricFormatter;

    public IslStatsBolt(String metricPrefix) {
        this.metricFormatter = new MetricFormatter(metricPrefix);
    }

    private static List<Object> tsdbTuple(String metric, long timestamp, Number value, Map<String, String> tag)
            throws IOException {
        Datapoint datapoint = new Datapoint(metric, timestamp, tag, value);
        return Collections.singletonList(Utils.MAPPER.writeValueAsString(datapoint));
    }

    @Override
    public void prepare(Map map, TopologyContext topologyContext, OutputCollector collector) {
        this.collector = collector;
    }

    List<Object> buildTsdbTuple(IslInfoData data, long timestamp) throws IOException {
        Map<String, String> tags = new HashMap<>();
        tags.put("src_switch", data.getSource().getSwitchId().toOtsdFormat());
        tags.put("src_port", String.valueOf(data.getSource().getPortNo()));
        tags.put("dst_switch", data.getDestination().getSwitchId().toOtsdFormat());
        tags.put("dst_port", String.valueOf(data.getDestination().getPortNo()));

        return tsdbTuple(metricFormatter.format("isl.latency"), timestamp, data.getLatency(), tags);
    }

    private String getJson(Tuple tuple) {
        return tuple.getString(0);
    }

    public Message getMessage(String json) throws IOException {
        return Utils.MAPPER.readValue(json, Message.class);
    }

    InfoData getInfoData(Message message) {
        if (!(message instanceof InfoMessage)) {
            throw new IllegalArgumentException(message.getClass().getName() + " is not an InfoMessage");
        }
        return ((InfoMessage) message).getData();
    }

    IslInfoData getIslInfoData(InfoData data) {
        if (!(data instanceof IslInfoData)) {
            throw new IllegalArgumentException(data.getClass().getName() + " is not an IslInfoData");
        }
        return (IslInfoData) data;
    }

    @Override
    public void execute(Tuple tuple) {
        logger.debug("tuple: {}", tuple);
        String json = getJson(tuple);
        try {
            Message message = getMessage(json);
            IslInfoData data = getIslInfoData(getInfoData(message));
            List<Object> results = buildTsdbTuple(data, message.getTimestamp());
            logger.debug("emit: {}", results);
            collector.emit(results);
        } catch (IOException e) {
            logger.error("Could not deserialize message={}", json, e);
        } catch (Exception e) {
            // TODO: has to be a cleaner way to do this?
        } finally {
            collector.ack(tuple);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(AbstractTopology.fieldMessage);
    }

}
