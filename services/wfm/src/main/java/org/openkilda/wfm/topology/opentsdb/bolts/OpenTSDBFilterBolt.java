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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.storm.opentsdb.bolt.TupleOpenTsdbDatapointMapper;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.Datapoint;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class OpenTSDBFilterBolt extends BaseRichBolt {

    private static final Logger LOGGER = LogManager.getLogger(OpenTSDBFilterBolt.class);
    private static final long TEN_MINUTES = 60000L;

    private static final Fields DECLARED_FIELDS =
            new Fields(TupleOpenTsdbDatapointMapper.DEFAULT_MAPPER.getMetricField(),
                    TupleOpenTsdbDatapointMapper.DEFAULT_MAPPER.getTimestampField(),
                    TupleOpenTsdbDatapointMapper.DEFAULT_MAPPER.getValueField(),
                    TupleOpenTsdbDatapointMapper.DEFAULT_MAPPER.getTagsField());

    private Long prevTimestamp = 0L;
    private Datapoint prevDatapoint = new Datapoint();

    private OutputCollector collector;

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        this.collector = collector;
    }

    @Override
    public void execute(Tuple tuple) {
        final String data = tuple.getString(0);
        try {
            InfoMessage infoMessage = MAPPER.readValue(data, InfoMessage.class);
            Datapoint datapoint = (Datapoint) infoMessage.getData();
            LOGGER.info("Processing datapoint with correlationId {}", infoMessage.getCorrelationId());

            if (!Objects.equals(datapoint, prevDatapoint) && datapoint.getTimestamp() - prevTimestamp > TEN_MINUTES) {
                prevTimestamp = datapoint.getTimestamp();
                prevDatapoint = datapoint;

                List<Object> stream = Stream.of(datapoint.getMetric(), datapoint.getTimestamp(), datapoint.getValue(),
                        datapoint.getTags())
                        .collect(Collectors.toList());

                LOGGER.debug("emit: " + stream);
                collector.emit(stream);
            }
        } catch (IOException e) {
            LOGGER.error("Failed read datapoint");
        } finally {
            collector.ack(tuple);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(DECLARED_FIELDS);
    }
}
