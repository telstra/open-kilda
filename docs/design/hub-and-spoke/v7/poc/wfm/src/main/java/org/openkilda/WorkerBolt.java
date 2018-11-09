package org.openkilda;

import static org.openkilda.Constants.BOLT_COORDINATOR;
import static org.openkilda.Constants.BOLT_HUB;
import static org.openkilda.Constants.SPOUT_WORKER;
import static org.openkilda.Constants.STREAM_TO_BOLT_COORDINATOR;
import static org.openkilda.Constants.STREAM_WORKER_BOLT_TO_FL;
import static org.openkilda.Constants.STREAM_WORKER_BOLT_TO_HUB_BOLT;

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

@Slf4j
public class WorkerBolt extends BaseRichBolt {

    private Map<String, Integer> state = new HashMap<>();
    private OutputCollector collector;
    private StormToKafkaTranslator mapper = new StormToKafkaTranslator();


    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        this.collector = collector;
    }

    @Override
    public void execute(Tuple input) {
        collector.ack(input);
        String key = mapper.getKeyFromTuple(input);
        String parentKey = key.substring(0, 32);

        if (input.getSourceComponent().equals(BOLT_HUB)) {
            log.info("pass message to fl");
            collector.emit(STREAM_TO_BOLT_COORDINATOR, new Values(
                    key, CoordinatorCommand.REQUEST_CALLBACK.name(),
                    100L,
                    key
            ));
            state.put(key, input.getSourceTask());

            collector.emit(STREAM_WORKER_BOLT_TO_FL, new Values(
                    key,
                    mapper.getMessageFromTuple(input)));

        } else if (input.getSourceComponent().equals(SPOUT_WORKER)) {
            if (state.containsKey(key)) {
                int taskId = state.remove(key);
                log.info("pass response to hub");
                collector.emitDirect(taskId, STREAM_WORKER_BOLT_TO_HUB_BOLT, new Values(
                        parentKey,
                        mapper.getMessageFromTuple(input)));
                log.info("clear callback");
                collector.emit(STREAM_TO_BOLT_COORDINATOR, new Values(
                        key, CoordinatorCommand.CANCEL_CALLBACK.name(),
                        0,
                        null
                ));
            } else {
                log.error("missed groping for worker bolt");
            }
        } else if (input.getSourceComponent().equals(BOLT_COORDINATOR)) {
            if (state.containsKey(key)) {
                log.info("timeout callback received. going to cleanup");
                log.info("pass failed response to hub");
                int taskId = state.remove(key);
                collector.emitDirect(taskId, STREAM_WORKER_BOLT_TO_HUB_BOLT, new Values(
                        parentKey,
                        "some error"));
            }
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declareStream(STREAM_TO_BOLT_COORDINATOR, new Fields("key", "command", "timeout", "context"));

        Fields fields = new Fields(StormToKafkaTranslator.BOLT_KEY,
                StormToKafkaTranslator.BOLT_MESSAGE);

        declarer.declareStream(STREAM_WORKER_BOLT_TO_FL, fields);
        declarer.declareStream(STREAM_WORKER_BOLT_TO_HUB_BOLT, true, fields);
    }
}