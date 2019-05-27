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

package org.openkilda.wfm.topology.ping.bolt;

import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.error.PipelineException;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.utils.Utils;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

public class TickDeduplicator extends AbstractBolt {
    public static final String BOLT_ID = ComponentId.TICK_DEDUPLICATOR.toString();

    public static final Fields STREAM_FIELDS = MonotonicTick.STREAM_FIELDS;

    public static final String STREAM_PING_ID = MonotonicTick.STREAM_PING_ID;
    public static final Fields STREAM_PING_FIELDS = MonotonicTick.STREAM_PING_FIELDS;

    private final HashMap<Integer, Long> lastTick = new HashMap<>();
    private Integer activeSourceTask = null;
    private final long tickPeriod;

    public TickDeduplicator(long tickPeriod, TimeUnit unit) {
        this.tickPeriod = unit.toMillis(tickPeriod);
    }

    @Override
    protected void handleInput(Tuple input) throws Exception {
        int taskId = input.getSourceTask();
        String stream = input.getSourceStreamId();

        if (Utils.DEFAULT_STREAM_ID.equals(stream)) {
            updateLastTick(taskId, pullTick(input));
        }

        if (shouldProxy(taskId)) {
            log.debug("Proxy tuple in stream {} from {}", stream, taskId);
            getOutput().emit(stream, input, input.getValues());
        }
    }

    private void updateLastTick(int taskId, long tick) {
        lastTick.put(taskId, tick);

        if (activeSourceTask == null) {
            activeSourceTask = taskId;
            log.debug("Set {} as active source (no previous)", activeSourceTask);
        } else {
            Long lastActiveTick = lastTick.get(activeSourceTask);
            if (lastActiveTick + tickPeriod * 2 < tick) {
                log.debug(
                        "Switch active source {} => {} (delay from last tick {}ms)",
                        activeSourceTask, taskId, tick - lastActiveTick);
                activeSourceTask = taskId;
            }
        }
    }

    private boolean shouldProxy(int taskId) {
        return activeSourceTask == taskId;
    }

    private Long pullTick(Tuple input) throws PipelineException {
        return pullValue(input, MonotonicTick.FIELD_ID_TIME_MILLIS, Long.class);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputManager) {
        outputManager.declare(STREAM_FIELDS);
        outputManager.declareStream(STREAM_PING_ID, STREAM_PING_FIELDS);
    }
}
