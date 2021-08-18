/* Copyright 2019 Telstra Open Source
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

package org.openkilda.wfm.share.hubandspoke;

import static java.lang.String.format;
import static org.openkilda.wfm.share.hubandspoke.CoordinatedBolt.COMMAND_FIELD;
import static org.openkilda.wfm.share.hubandspoke.CoordinatedBolt.TIMEOUT_FIELD;

import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.topology.utils.MessageKafkaTranslator;

import com.google.common.annotations.VisibleForTesting;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Storm bolt that keeps track of duration of operation's execution and then sends callback to the requester.
 */
@Slf4j
public final class CoordinatorBolt extends AbstractBolt {
    public static final String ID = "coordinator.bolt";
    public static final String INCOME_STREAM = "coordinator.command";
    public static final Fields FIELDS_KEY = new Fields(MessageKafkaTranslator.FIELD_ID_KEY);

    private Map<String, Callback> callbacks = new HashMap<>();
    private SortedMap<Long, Set<String>> timeouts = new TreeMap<>();

    @Override
    protected void handleInput(Tuple input) {
        if (CoordinatorSpout.ID.equals(input.getSourceComponent())) {
            Long currentTime = input.getLongByField(CoordinatorSpout.FIELD_ID_TIME_MS);
            tick(currentTime);
        } else {
            handleCommand(input);
        }
    }

    private void handleCommand(Tuple input) {
        String key = input.getStringByField(MessageKafkaTranslator.FIELD_ID_KEY);
        CoordinatorCommand command = (CoordinatorCommand) input.getValueByField(COMMAND_FIELD);
        switch (command) {
            case REQUEST_CALLBACK:
                Object context = input.getValueByField(FIELD_ID_CONTEXT);
                int timeout = input.getIntegerByField(TIMEOUT_FIELD);
                int taskId = input.getSourceTask();

                registerCallback(key, context, timeout, taskId);
                break;
            case CANCEL_CALLBACK:
                cancelCallback(key);
                break;
            default:
                throw new UnsupportedOperationException(format("Unsupported command for coordinator: %s", command));
        }
    }

    @VisibleForTesting
    void registerCallback(String key, Object context, int timeout, int taskId) {
        log.trace("Adding callback for {} with timeout {}", key, timeout);
        long triggerTime = System.currentTimeMillis() + timeout;
        timeouts.computeIfAbsent(triggerTime, mappingFunction -> new HashSet<>())
                .add(key);

        Values value = new Values(key, context);
        callbacks.put(key, Callback.of(taskId, value));
    }

    @VisibleForTesting
    void cancelCallback(String key) {
        if (callbacks.remove(key) == null) {
            log.warn("{} is already cancelled", key);
        } else {
            log.debug("Request processing of {} is finished", key);
        }
    }

    /**
     * Check if the time is out for any of pending requests, if so - remove such requests and send callbacks.
     */
    @VisibleForTesting
    void tick(Long currentTime) {
        SortedMap<Long, Set<String>> outdatedCallbacks = timeouts.headMap(currentTime);
        for (Set<String> callbacks : outdatedCallbacks.values()) {
            callbacks.stream()
                    .map(this.callbacks::remove)
                    .filter(Objects::nonNull)
                    .forEach(callback -> getOutput().emitDirect(callback.taskId, callback.context));
        }

        outdatedCallbacks.clear();
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        Fields fields = new Fields(MessageKafkaTranslator.FIELD_ID_KEY, FIELD_ID_CONTEXT);
        declarer.declare(true, fields);
    }

    @VisibleForTesting
    Map<String, Callback> getCallbacks() {
        return callbacks;
    }

    @VisibleForTesting
    SortedMap<Long, Set<String>> getTimeouts() {
        return timeouts;
    }

    public enum CoordinatorCommand {
        REQUEST_CALLBACK,
        CANCEL_CALLBACK
    }

    @Value(staticConstructor = "of")
    private static class Callback {
        private final int taskId;
        private final Values context;
    }
}
