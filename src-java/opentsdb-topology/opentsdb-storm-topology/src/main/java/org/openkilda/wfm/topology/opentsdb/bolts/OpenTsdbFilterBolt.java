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

package org.openkilda.wfm.topology.opentsdb.bolts;

import org.openkilda.messaging.info.Datapoint;

import lombok.Value;
import org.apache.storm.Config;
import org.apache.storm.Constants;
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

public class OpenTsdbFilterBolt extends BaseRichBolt {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenTsdbFilterBolt.class);
    private static final long MUTE_IF_NO_UPDATES_SECS = TimeUnit.MINUTES.toSeconds(10);
    private static final long MUTE_IF_NO_UPDATES_MILLIS = TimeUnit.SECONDS.toMillis(MUTE_IF_NO_UPDATES_SECS);

    public static final String FIELD_ID_DATAPOINT = "datapoint";

    public static final Fields STREAM_FIELDS = new Fields(FIELD_ID_DATAPOINT);

    private Map<DatapointKey, Datapoint> storage = new HashMap<>();
    private OutputCollector collector;

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        this.collector = collector;
    }
    
    @Override
    public Map<String, Object> getComponentConfiguration() {
        Config conf = new Config();
        conf.put(Config.TOPOLOGY_TICK_TUPLE_FREQ_SECS, MUTE_IF_NO_UPDATES_SECS);
        return conf;
    }

    @Override
    public void execute(Tuple tuple) {
        
        if (isTickTuple(tuple)) {
            // opentsdb using current epoch time (date +%s) in seconds
            long now  = System.currentTimeMillis();
            storage.entrySet().removeIf(entry ->  now - entry.getValue().getTime() > MUTE_IF_NO_UPDATES_MILLIS);

            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("storage after clean tuple: {}", storage.toString());
            }

            collector.ack(tuple);
            return;
        }
        
        if (!tuple.contains("datapoint")) { //TODO: Should make sure tuple comes from correct bolt, ie not TickTuple
            collector.ack(tuple);
            return;
        }

        Datapoint datapoint = (Datapoint) tuple.getValueByField("datapoint");
        if (isUpdateRequired(datapoint)) {
            addDatapoint(datapoint);

            LOGGER.debug("emit datapoint: {}", datapoint);
            collector.emit(tuple, makeDefaultTuple(datapoint));
        } else {
            LOGGER.debug("skip datapoint: {}", datapoint);
        }

        collector.ack(tuple);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(STREAM_FIELDS);
    }

    private void addDatapoint(Datapoint datapoint) {
        LOGGER.debug("adding datapoint: {}", datapoint);
        LOGGER.debug("storage.size: {}", storage.size());
        storage.put(new DatapointKey(datapoint.getMetric(), datapoint.getTags()), datapoint);
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("addDatapoint storage: {}", storage.toString());
        }
    }

    private boolean isUpdateRequired(Datapoint datapoint) {
        boolean update = true;
        Datapoint prevDatapoint = storage.get(new DatapointKey(datapoint.getMetric(), datapoint.getTags()));

        if (prevDatapoint != null) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("prev: {} cur: {} equals: {} time_delta: {}",
                        prevDatapoint,
                        datapoint,
                        prevDatapoint.getValue().equals(datapoint.getValue()),
                        datapoint.getTime() - prevDatapoint.getTime()
                );
            }
            update = !prevDatapoint.getValue().equals(datapoint.getValue())
                    || datapoint.getTime() - prevDatapoint.getTime() >= MUTE_IF_NO_UPDATES_MILLIS;
        }
        return update;
    }
    
    private boolean isTickTuple(Tuple tuple) {
        String sourceComponent = tuple.getSourceComponent();
        String sourceStreamId = tuple.getSourceStreamId();
        
        return Constants.SYSTEM_COMPONENT_ID.equals(sourceComponent)
                && Constants.SYSTEM_TICK_STREAM_ID.equals(sourceStreamId);
    }

    private Values makeDefaultTuple(Datapoint datapoint) {
        return new Values(datapoint);
    }

    @Value
    private static class DatapointKey {

        private String metric;

        private Map<String, String> tags;
    }
}
