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

import static org.openkilda.messaging.Utils.MAPPER;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.apache.storm.opentsdb.bolt.TupleOpenTsdbDatapointMapper;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.openkilda.messaging.info.Datapoint;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class OpenTSDBFilterBolt extends BaseRichBolt {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenTSDBFilterBolt.class);
    private static final long TEN_MINUTES = 60000L;

    private static final Fields DECLARED_FIELDS =
            new Fields(TupleOpenTsdbDatapointMapper.DEFAULT_MAPPER.getMetricField(),
                    TupleOpenTsdbDatapointMapper.DEFAULT_MAPPER.getTimestampField(),
                    TupleOpenTsdbDatapointMapper.DEFAULT_MAPPER.getValueField(),
                    TupleOpenTsdbDatapointMapper.DEFAULT_MAPPER.getTagsField());

    //    private Set<Datapoint> storage = new HashSet<>();
    private ConcurrentMap<Integer, Datapoint> storage = new ConcurrentHashMap<>();
    private OutputCollector collector;

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        this.collector = collector;
    }

    @Override
    public void execute(Tuple tuple) {
        if (!tuple.contains("datapoint")) { //TODO: Should make sure tuple comes from correct bolt, ie not TickTuple
            collector.ack(tuple);
            return;
        }

        Datapoint datapoint = (Datapoint) tuple.getValueByField("datapoint");


        if (isUpdateRequired(datapoint)) {
            addDatapoint(datapoint);

            List<Object> stream = Stream.of(datapoint.getMetric(), datapoint.getTime(), datapoint.getValue(),
                    datapoint.getTags())
                    .collect(Collectors.toList());

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
        LOGGER.info("adding datapoint: " + datapoint.hashCode());
        LOGGER.info("storage.size: " + storage.size());
        storage.put(datapoint.hashCode(), datapoint);
    }

    private boolean isUpdateRequired(Datapoint datapoint) {
        boolean update = true;
        if (storage.containsKey(datapoint.hashCode())) {
            Datapoint prevDatapoint = storage.get(datapoint.hashCode());
            update = !prevDatapoint.getValue().equals(datapoint.getValue()) ||
                    datapoint.getTime() - prevDatapoint.getTime() >= TEN_MINUTES;
        }
        return update;
    }
}
