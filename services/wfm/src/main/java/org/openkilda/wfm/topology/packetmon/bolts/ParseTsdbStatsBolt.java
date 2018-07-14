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

package org.openkilda.wfm.topology.packetmon.bolts;

import static org.openkilda.messaging.Utils.MAPPER;

import org.openkilda.messaging.info.Datapoint;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ParseTsdbStatsBolt extends BaseRichBolt {
    public static final String FlowMon_Stream = "flowmon_stream";
    private OutputCollector collector;
    private static final Logger logger = LoggerFactory.getLogger(ParseTsdbStatsBolt.class);
    public static final Fields  fields = new Fields("hash", "datapoint");

    private boolean isFlowPacketMetric(Datapoint datapoint) {
        return datapoint.getMetric().equals("pen.flow.packets")
                || datapoint.getMetric().equals("pen.flow.ingress.packets");
    }

    @Override
    public void prepare(Map map, TopologyContext topologyContext, OutputCollector outputCollector) {
        collector = outputCollector;
    }

    @Override
    public void execute(Tuple tuple) {
        final String data = tuple.getString(0);
        try {
            Datapoint datapoint = MAPPER.readValue(data, Datapoint.class);
            // If datapoint represents a flow value, either ingress or egress, then send to FlowMonBolt
            if (isFlowPacketMetric(datapoint)) {
                List<Object> stream = Stream.of(datapoint.simpleHashCode(), datapoint).collect(Collectors.toList());
                collector.emit(FlowMon_Stream, stream);
            }
        } catch (Exception e) {
            logger.error("Failed reading data: " + data, e);
        } finally {
            collector.ack(tuple);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        outputFieldsDeclarer.declareStream(FlowMon_Stream, fields);
    }
}
