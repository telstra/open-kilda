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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class BadFlowBolt extends BaseRichBolt {
    private static final Logger logger = LoggerFactory.getLogger(ParseTsdbStatsBolt.class);
    private static final String metric = "pen.flow.flowmon.variance";
    public static final String TsdbStream = "TsdbStream";

    private LoadingCache<String, Datapoint> datapointCache;
    private OutputCollector collector;
    private int cacheTimeoutSeconds;

    public BadFlowBolt(int cacheTimeoutSeconds) {
        this.cacheTimeoutSeconds = cacheTimeoutSeconds;
    }

    @Override
    public void prepare(Map map, TopologyContext topologyContext, OutputCollector outputCollector) {
        collector = outputCollector;

        RemovalListener<String, Datapoint> removalListener;
        removalListener = removalNotification -> {
            if (removalNotification.wasEvicted()) {
                logger.warn("{} was removed from the datapointCache of BadFlowMon because {}.",
                        removalNotification.getValue().getTags().get("flowid"),
                        removalNotification.getCause().toString());
            }
        };

        datapointCache = CacheBuilder.newBuilder()
                .expireAfterAccess(cacheTimeoutSeconds, TimeUnit.SECONDS)
                .removalListener(removalListener)
                .build(new CacheLoader<String, Datapoint>() {
                    @Override
                    public Datapoint load(String s) {
                        Datapoint datapoint = new Datapoint();
                        datapoint.setMetric(metric);
                        return datapoint;
                    }
                });

    }

    /**
     * When a tuple is received log an ERROR and send to OpenTsdb.
     * TODO: get smarter in how to deal with broken flows, second verification point, and then auto-fix.
     *
     * @param tuple Received tuple
     */
    @Override
    public void execute(Tuple tuple) {
        if (tuple.getSourceStreamId().equals(FlowMonBolt.BadFlowStream)) {
            if (tuple.getFields().equals(FlowMonBolt.fields)) {
                logger.error("{} needs further verification as variance is {} for the {} direction at {}",
                        tuple.getStringByField("flowid"),
                        tuple.getDoubleByField("variance"),
                        tuple.getStringByField("direction"),
                        tuple.getLongByField("timestamp"));

                emitToTsdbTopic(getDatapoint(tuple));
            }
        }
        collector.ack(tuple);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        outputFieldsDeclarer.declareStream(TsdbStream, new Fields("message"));
    }

    /**
     * Using a cache to prevent creation of a new datapoint and Map for tags.
     *
     * @param tuple Received Tuple.
     * @return Datapoint
     */
    private Datapoint getDatapoint(Tuple tuple) {

        Datapoint datapoint = datapointCache.getUnchecked(tuple.getStringByField("flowid")
                .concat(tuple.getStringByField("direction")));
        if (datapoint.getTags().isEmpty()) {
            Map<String, String> tags = new HashMap<>();
            tags.put("flowid", tuple.getStringByField("flowid"));
            tags.put("direction", tuple.getStringByField("direction"));
            datapoint.setTags(tags);
        }
        datapoint.setTimestamp(tuple.getLongByField("timestamp"));
        datapoint.setValue(tuple.getDoubleByField("variance"));

        return datapoint;
    }

    private void emitToTsdbTopic(Datapoint datapoint) {
        collector.emit(TsdbStream, new Values(datapoint));
    }
}
