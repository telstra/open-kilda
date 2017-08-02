package org.bitbucket.openkilda.wfm.topology;

import org.apache.storm.task.IOutputCollector;
import org.apache.storm.tuple.Tuple;

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
