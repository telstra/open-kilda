package org.openkilda;

import static org.openkilda.Constants.SPOUT_COORDINATOR;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;

@Slf4j
public class CoordinatorBolt extends BaseRichBolt {

    private Map<String, Callback> callbacks = new HashMap<>();
    private SortedMap<Long, String> timeouts = new TreeMap<>();
    private OutputCollector collector;

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        this.collector = collector;
    }

    @Override
    public void execute(Tuple input) {
        collector.ack(input);
        if (input.getSourceComponent().equals(SPOUT_COORDINATOR)) {
            tick();
        } else {
            handleCommand(input);
        }
    }

    private void handleCommand(Tuple input) {
        String key = input.getStringByField("key");
        CoordinatorCommand command = CoordinatorCommand.valueOf(input.getStringByField("command"));
        switch (command) {
            case REQUEST_CALLBACK:
                Object context = input.getValueByField("context");
                Values value = new Values(key, context);
                callbacks.put(key, Callback.of(value, input.getSourceTask()));
                Long timeout = input.getLongByField("timeout");
                timeouts.put(System.currentTimeMillis() + timeout, key);
                break;
            case CANCEL_CALLBACK:
                callbacks.remove(key);
                break;
            default:
                log.error("invalid command");
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        Fields fields = new Fields("key", "context");
        declarer.declare(true, fields);
    }

    private void tick() {
        SortedMap<Long, String> range = timeouts.subMap(0L, System.currentTimeMillis());
        for (Entry<Long, String> e : range.entrySet()) {
            timeouts.remove(e.getKey());
            Callback callback = callbacks.remove(e.getValue());
            if (callback != null) {
                collector.emitDirect(callback.taskId, callback.values);
            }
        }
    }

    private static @Value(staticConstructor = "of")
    class Callback {
        private final Values values;
        private final int taskId;
    }
}