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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.storm.Config;
import org.apache.storm.Constants;
import org.apache.storm.opentsdb.bolt.TupleOpenTsdbDatapointMapper;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.openkilda.messaging.info.Datapoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class OpenTSDBFilterBolt extends BaseRichBolt {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenTSDBFilterBolt.class);
    private static final long TEN_MINUTES = TimeUnit.SECONDS.convert(10, TimeUnit.MINUTES);
    
    private static final Fields DECLARED_FIELDS =
            new Fields(TupleOpenTsdbDatapointMapper.DEFAULT_MAPPER.getMetricField(),
                    TupleOpenTsdbDatapointMapper.DEFAULT_MAPPER.getTimestampField(),
                    TupleOpenTsdbDatapointMapper.DEFAULT_MAPPER.getValueField(),
                    TupleOpenTsdbDatapointMapper.DEFAULT_MAPPER.getTagsField());

    private Map<Integer, Datapoint> storage = new HashMap<>();
    private OutputCollector collector;

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        this.collector = collector;
    }
    
    @Override
    public Map<String, Object> getComponentConfiguration() {
        Config conf = new Config();
        conf.put(Config.TOPOLOGY_TICK_TUPLE_FREQ_SECS, TEN_MINUTES);
        return conf;
    }


    @Override
    public void execute(Tuple tuple) {
        
        if (isTickTuple(tuple)) {
            Set<Integer> keys = storage.keySet();
            // opentsdb using current epoch time (date +%s) in seconds
            long now  = System.currentTimeMillis()/1000;
            for (Integer key: keys) {
                storage.compute(key, (k, v) -> now - v.getTime() > TEN_MINUTES ? null: v);
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

            List<Object> stream = Stream.of(datapoint.getMetric(), datapoint.getTime(), datapoint.getValue(),
                    datapoint.getTags()).collect(Collectors.toList());

            LOGGER.debug("emit: " + stream);
            collector.emit(stream);
        }
        collector.ack(tuple);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(DECLARED_FIELDS);
    }

    private void addDatapoint(Datapoint datapoint) {
        LOGGER.debug("adding datapoint: " + datapoint.simpleHashCode());
        LOGGER.debug("storage.size: " + storage.size());
        storage.put(datapoint.simpleHashCode(), datapoint);
    }

    private boolean isUpdateRequired(Datapoint datapoint) {
        boolean update = true;
        if (storage.containsKey(datapoint.simpleHashCode())) {
            Datapoint prevDatapoint = storage.get(datapoint.simpleHashCode());
            update = !prevDatapoint.getValue().equals(datapoint.getValue()) ||
                    datapoint.getTime() - prevDatapoint.getTime() >= TEN_MINUTES;
        }
        return update;
    }
    
    private boolean isTickTuple(Tuple tuple) {
        String sourceComponent = tuple.getSourceComponent();
        String sourceStreamId = tuple.getSourceStreamId();
        
        return sourceComponent.equals(Constants.SYSTEM_COMPONENT_ID) &&
                sourceStreamId.equals(Constants.SYSTEM_TICK_STREAM_ID);
    }
}
