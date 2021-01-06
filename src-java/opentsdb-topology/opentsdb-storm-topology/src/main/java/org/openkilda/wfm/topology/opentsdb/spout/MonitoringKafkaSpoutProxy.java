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

package org.openkilda.wfm.topology.opentsdb.spout;

import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.topology.opentsdb.spout.MonitoringTupleIdGenerator.MonitoringTupleId;

import lombok.extern.slf4j.Slf4j;
import org.apache.storm.kafka.spout.KafkaSpout;
import org.apache.storm.spout.SpoutOutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichSpout;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Values;

import java.util.Map;

@Slf4j
public class MonitoringKafkaSpoutProxy<K, V> extends BaseRichSpout {
    public static final String FIELD_ID_COMMAND = "command";

    public static final String STREAM_PERFORMANCE_ID = "performance.monitor";
    public static final Fields STREAM_PERFORMANCE_FIELDS = new Fields(FIELD_ID_COMMAND, AbstractBolt.FIELD_ID_CONTEXT);

    private final KafkaSpout<K, V> target;

    private transient SpoutOutputCollector collector;
    private transient MonitoringTupleIdGenerator tupleIdGenerator;

    private long successCount = 0;
    private long failCount = 0;

    public MonitoringKafkaSpoutProxy(KafkaSpout<K, V> target) {
        this.target = target;
    }

    @Override
    public void open(Map conf, TopologyContext context, SpoutOutputCollector collector) {
        this.collector = collector;
        tupleIdGenerator = new MonitoringTupleIdGenerator();

        target.open(conf, context, collector);
    }

    @Override
    public void activate() {
        super.activate();
        target.activate();
    }

    @Override
    public void deactivate() {
        super.deactivate();
        target.deactivate();
    }

    @Override
    public void close() {
        super.close();
        target.close();
    }

    @Override
    public Map<String, Object> getComponentConfiguration() {
        return target.getComponentConfiguration();
    }

    @Override
    public void nextTuple() {
        emitPerformanceCounters();
        target.nextTuple();
    }

    @Override
    public void ack(Object msgId) {
        if (! (msgId instanceof MonitoringTupleId)) {
            successCount += 1;
            target.ack(msgId);
        }
    }

    @Override
    public void fail(Object msgId) {
        if (msgId instanceof MonitoringTupleId) {
            log.error(
                    "Tuple produced by {} with id {} have failed",
                    getClass().getName(), ((MonitoringTupleId) msgId).getId());
        } else {
            failCount += 1;
            target.fail(msgId);
        }
    }

    private void emitPerformanceCounters() {
        if (successCount == 0 && failCount == 0) {
            log.debug("performance counters are empty");
            return;
        }

        log.debug("Emit performance counters: success={}, fail={}", successCount, failCount);
        PerformanceCountersUpdateCommand command = new PerformanceCountersUpdateCommand(successCount, failCount);
        collector.emit(STREAM_PERFORMANCE_ID, makePerformanceTuple(command), tupleIdGenerator.generate());

        successCount = failCount = 0;
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputManager) {
        target.declareOutputFields(outputManager);
        outputManager.declareStream(STREAM_PERFORMANCE_ID, STREAM_PERFORMANCE_FIELDS);
    }

    private Values makePerformanceTuple(KafkaSpoutCommand command) {
        return new Values(command, new CommandContext());
    }
}
