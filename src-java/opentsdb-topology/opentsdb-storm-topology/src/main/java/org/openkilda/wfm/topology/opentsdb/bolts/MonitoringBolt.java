/* Copyright 2021 Telstra Open Source
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

import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.topology.opentsdb.spout.KafkaSpoutCommand;
import org.openkilda.wfm.topology.opentsdb.spout.KafkaSpoutHandler;
import org.openkilda.wfm.topology.opentsdb.spout.MonitoringKafkaSpoutProxy;

import lombok.extern.slf4j.Slf4j;
import org.apache.storm.opentsdb.bolt.TupleOpenTsdbDatapointMapper;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.apache.storm.utils.TupleUtils;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class MonitoringBolt extends AbstractBolt implements KafkaSpoutHandler {
    public static final String BOLT_ID = "monitoring";

    public static final Fields STREAM_FIELDS = new Fields(
            TupleOpenTsdbDatapointMapper.DEFAULT_MAPPER.getMetricField(),
            TupleOpenTsdbDatapointMapper.DEFAULT_MAPPER.getTimestampField(),
            TupleOpenTsdbDatapointMapper.DEFAULT_MAPPER.getValueField(),
            TupleOpenTsdbDatapointMapper.DEFAULT_MAPPER.getTagsField());

    private final int flushIntervalInSeconds;
    private transient Clock clock;

    private int taskId = 0;

    private long successCount = 0;
    private long failCount = 0;

    public MonitoringBolt(int flushIntervalInSeconds) {
        super();
        this.flushIntervalInSeconds = flushIntervalInSeconds;
    }

    @Override
    public void updatePerformanceCounters(long success, long fail) {
        successCount += success;
        failCount += fail;
    }

    @Override
    protected void handleInput(Tuple input) throws Exception {
        if (MonitoringKafkaSpoutProxy.STREAM_PERFORMANCE_ID.equals(input.getSourceStreamId())) {
            handleCommand(input);
        } else if (TupleUtils.isTick(input)) {
            handleFlush();
        } else {
            unhandledInput(input);
        }
    }

    private void handleCommand(Tuple input) throws PipelineException {
        KafkaSpoutCommand command = pullValue(
                input, MonitoringKafkaSpoutProxy.FIELD_ID_COMMAND, KafkaSpoutCommand.class);
        handleCommand(command);
    }

    private void handleCommand(KafkaSpoutCommand command) {
        command.apply(this);
    }

    private void handleFlush() {
        log.info("Monitoring flush: success={}, fail={}", successCount, failCount);
        emitCounter("poc.otsdb.performance.success", successCount);
        emitCounter("poc.otsdb.performance.fail", failCount);
    }

    private void emitCounter(String metric, long value) {
        Map<String, String> tags = new HashMap<>(2);
        tags.put("topology", "opentsdb");
        tags.put("taskId", Integer.toString(taskId));
        emit(getCurrentTuple(), makeDefaultTuple(metric, clock.instant().toEpochMilli(), value, tags));
    }

    private Values makeDefaultTuple(String metricName, long timestamp, Number value, Map<String, String> tags) {
        return new Values(metricName, timestamp, value, tags);
    }

    @Override
    protected void init() {
        super.init();
        clock = Clock.systemUTC();
    }

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        super.prepare(stormConf, context, collector);
        taskId = context.getThisTaskId();
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(STREAM_FIELDS);
    }

    @Override
    public Map<String, Object> getComponentConfiguration() {
        return TupleUtils.putTickFrequencyIntoComponentConfig(
                super.getComponentConfiguration(), flushIntervalInSeconds);
    }
}
