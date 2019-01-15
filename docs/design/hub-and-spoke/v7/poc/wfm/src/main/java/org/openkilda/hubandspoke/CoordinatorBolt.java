package org.openkilda.hubandspoke;

import static org.openkilda.hubandspoke.Constants.SPOUT_COORDINATOR;

import lombok.Value;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;


public class CoordinatorBolt extends BaseRichBolt {

    private static final Logger log = LoggerFactory.getLogger(CoordinatorBolt.class);

    private OutputCollector collector;

    private Map<String, Callback> callbacks = new HashMap<>();
    private SortedMap<Long, String> timeouts = new TreeMap<>();

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

                if (callbacks.containsKey(key)) {
                    Callback callback = callbacks.remove(key);
                    timeouts.remove(callback.triggerTime);
                }

                Object context = input.getValueByField("context");
                Values value = new Values(key, context);
                Long timeout = input.getLongByField("timeout");
                long triggerTime = System.currentTimeMillis() + timeout;
                timeouts.put(triggerTime, key);
                callbacks.put(key, Callback.of(value, input.getSourceTask(), triggerTime));

                break;
            case CANCEL_CALLBACK:
                callbacks.remove(key);
                break;
            default:
                log.error("Unexpected command for CoordinatorBolt");
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

    public enum CoordinatorCommand {
        REQUEST_CALLBACK,
        CANCEL_CALLBACK
    }

    private static @Value(staticConstructor = "of")
    class Callback {
        private final Values values;
        private final int taskId;
        private final Long triggerTime;
    }
}