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

import org.openkilda.messaging.info.Datapoint;
import org.openkilda.wfm.topology.packetmon.data.FlowStats;
import org.openkilda.wfm.topology.packetmon.type.FlowDirection;
import org.openkilda.wfm.topology.packetmon.type.FlowType;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FlowMonBolt extends BaseRichBolt {
    public static final String BadFlowStream = "BadFlowStream";
    public static final Fields fields = new Fields("flowid", "direction", "timestamp", "variance");
    private static final Logger logger = LoggerFactory.getLogger(ParseTsdbStatsBolt.class);
    private int cacheTimeoutSeconds;
    private int windowSize;
    private int maxTimeDelta;
    private int maxFlowVariance;

    private LoadingCache<String, FlowStats> cache;
    private OutputCollector collector;

    public FlowMonBolt(int cacheTimeoutSeconds, int windowSize, int maxTimeDelta, int maxFlowVariance) {
        this.cacheTimeoutSeconds = cacheTimeoutSeconds;
        this.windowSize = windowSize;
        this.maxTimeDelta = maxTimeDelta;
        this.maxFlowVariance = maxFlowVariance;
    }

    @Override
    public void prepare(Map map, TopologyContext topologyContext, OutputCollector outputCollector) {
        collector = outputCollector;

        cache = CacheBuilder.newBuilder()
                .expireAfterAccess(cacheTimeoutSeconds, TimeUnit.SECONDS)
                .build(new CacheLoader<String, FlowStats>() {
                    @Override
                    public FlowStats load(String s) {
                        return new FlowStats(s, windowSize, maxTimeDelta, maxFlowVariance);
                    }
                });
    }

    @Override
    public void execute(Tuple tuple) {
        if (tuple.getFields().contains("datapoint")) {
            addAndCheckDatapoint((Datapoint) tuple.getValueByField("datapoint"));
        }
        collector.ack(tuple);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        outputFieldsDeclarer.declareStream(BadFlowStream, fields);
    }

    public LoadingCache<String, FlowStats> getCache() {
        return cache;
    }

    private void emit(String streamName, List<Object> stream) {
        collector.emit(streamName, stream);
    }

    private String getFlowId(Datapoint datapoint) {
        return datapoint.getTags().get("flowid");
    }

    private String getDirection(Datapoint datapoint) {
        return datapoint.getTags().get("direction");
    }

    private FlowDirection getDirectionAsType(Datapoint datapoint) throws IOException {
        if (getDirection(datapoint).equalsIgnoreCase("forward")) {
            return FlowDirection.forward;
        } else if (getDirection(datapoint).equalsIgnoreCase("reverse")) {
            return FlowDirection.reverse;
        }
        throw new IOException("unknown direction found");
    }

    private FlowType getType(Datapoint datapoint) throws IOException {
        if (datapoint.getMetric().equalsIgnoreCase("pen.flow.ingress.packets")) {
            return FlowType.INGRESS;
        } else if (datapoint.getMetric().equalsIgnoreCase("pen.flow.packets")) {
            return FlowType.EGRESS;
        }
        throw new IOException("unknown metric");
    }

    private void addAndCheckDatapoint(Datapoint datapoint) {
        FlowDirection direction;
        FlowType flowType;
        String flowId = getFlowId(datapoint);

        try {
            direction = getDirectionAsType(datapoint);
            flowType = getType(datapoint);
        } catch (IOException e) {
            logger.error("Invalid datapoint {}", datapoint.toString(), e);
            return;
        }

        FlowStats flowStats = cache.getUnchecked(flowId);
        flowStats.add(direction, flowType, datapoint.getTimestamp(), datapoint.getValue().longValue());
        if (!flowStats.ok(direction)) {
            List<Object> stream = Stream
                    .of(
                            getFlowId(datapoint),
                            getDirection(datapoint),
                            datapoint.getTimestamp(),
                            flowStats.variancePercentage(direction))
                    .collect(Collectors.toList());
            emit(BadFlowStream, stream);
        }
        return;
    }
}
