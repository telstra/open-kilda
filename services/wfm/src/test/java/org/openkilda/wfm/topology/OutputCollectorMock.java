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

package org.openkilda.wfm.topology;

import org.apache.storm.task.IOutputCollector;
import org.apache.storm.tuple.Tuple;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by atopilin on 31/07/2017.
 */
public class OutputCollectorMock implements IOutputCollector {
    private Map<String, AtomicInteger> messages = new ConcurrentHashMap<>();

    @Override
    public List<Integer> emit(String streamId, Collection<Tuple> anchors, List<Object> tuple) {
        AtomicInteger count = messages.computeIfAbsent(streamId, k -> new AtomicInteger(0));
        count.incrementAndGet();
        return null;
    }

    public List<Integer> emit(String streamId, Tuple anchor, List<Object> tuple) {
        return emit(streamId, Arrays.asList(anchor), tuple);
    }

    @Override
    public void emitDirect(int taskId, String streamId, Collection<Tuple> anchors, List<Object> tuple) {

    }

    @Override
    public void ack(Tuple input) {

    }

    @Override
    public void fail(Tuple input) {

    }

    @Override
    public void resetTimeout(Tuple input) {

    }

    @Override
    public void reportError(Throwable error) {

    }

    public int getMessagesCount(String streamId) {
        return messages.get(streamId).get();
    }
}
