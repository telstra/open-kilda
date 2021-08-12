/* Copyright 2018 Telstra Open Source
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

package org.openkilda.wfm.topology.portstate.bolt;

import org.openkilda.messaging.Utils;
import org.openkilda.messaging.info.Datapoint;
import org.openkilda.messaging.info.event.PortChangeType;
import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.wfm.share.utils.MetricFormatter;
import org.openkilda.wfm.topology.utils.KafkaRecordTranslator;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ParsePortInfoBolt extends BaseRichBolt {
    private static final Logger logger = LoggerFactory.getLogger(ParsePortInfoBolt.class);

    public static final Fields OUTGOING_STREAM_FIELDS = new Fields(KafkaRecordTranslator.FIELD_ID_PAYLOAD);

    private final String metricName;
    private transient OutputCollector collector;
    private transient Table<String, Integer, Map<String, String>> tagsTable;

    public ParsePortInfoBolt(String metricPrefix) {
        MetricFormatter metricFormatter = new MetricFormatter(metricPrefix);
        this.metricName = metricFormatter.format("switch.state");
    }

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
                    PortInfoData port = (PortInfoData) tuple
                            .getValueByField(TopoDiscoParseBolt.FIELD_NAME);

                    if (getStateAsInt(port) >= 0) {
                        List<Object> result = makeTsdbDatapoint(port);
                        logger.debug("Emitting: {}", result);
                        collector.emit(result);
                    } else if (logger.isDebugEnabled()) {
                        List<Object> result = makeTsdbDatapoint(port);
                        logger.debug("Skip: {}", result);
                    }

                } catch (IOException e) {
                    logger.error("Error creating tsdbDatapoint for: {}", tuple.toString(), e);
                }
            }
        } finally {
            collector.ack(tuple);
        }
    }

    private List<Object> makeTsdbDatapoint(PortInfoData data) throws IOException {
        return tsdbTuple(metricName,
                data.getTimestamp(),
                getStateAsInt(data),
                getTags(data));
    }

    private int getStateAsInt(PortInfoData data) {

        PortChangeType state = data.getState();

        if (state.equals(PortChangeType.UP) || state.equals(PortChangeType.ADD)) {
            return 1;
        }

        if (state.equals(PortChangeType.DOWN) || state.equals(PortChangeType.DELETE)) {
            return 0;
        }

        return -1; // We skip others state here
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
            tag.put("switchid", data.getSwitchId().toOtsdFormat());
            tag.put("port", String.valueOf(data.getPortNo()));
            tagsTable.put(data.getSwitchId().toString(), data.getPortNo(), tag);
        }
        return tag;
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(OUTGOING_STREAM_FIELDS);
    }
}
