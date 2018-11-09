package org.openkilda;

import static org.openkilda.Constants.BOLT_COORDINATOR;
import static org.openkilda.Constants.BOLT_WORKER;
import static org.openkilda.Constants.SPOUT_HUB;
import static org.openkilda.Constants.STREAM_HUB_BOLT_TO_NB;
import static org.openkilda.Constants.STREAM_HUB_BOLT_TO_WORKER_BOLT;
import static org.openkilda.Constants.STREAM_TO_BOLT_COORDINATOR;

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
public class HubBolt extends BaseRichBolt {

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
        if (input.getSourceComponent().equals(SPOUT_HUB)) {

            collector.emit(STREAM_TO_BOLT_COORDINATOR, new Values(
                    key, CoordinatorCommand.REQUEST_CALLBACK.name(),
                    1000L * 5,
                    key
            ));

            int hops = Integer.valueOf(mapper.getMessageFromTuple(input));
            state.put(key, hops);
            if (hops > 0) {
                log.info("pass messages to worker");

                for (int i = 0; i < hops; ++i) {
                    collector.emit(STREAM_HUB_BOLT_TO_WORKER_BOLT, new Values(
                            String.format("%s-%d", mapper.getKeyFromTuple(input), i),
                            String.format("operation %d", i)));
                }
            }
            else if (hops == -1) {
                log.info("pass message with error to worker");
                collector.emit(STREAM_HUB_BOLT_TO_WORKER_BOLT, new Values(
                        String.format("%s-%d", mapper.getKeyFromTuple(input), -1),
                        String.format("operation %d", -1)));
            }
            else {
                log.info("error in hub. do nothing and waiting for callback");
            }
        } else if (input.getSourceComponent().equals(BOLT_WORKER)) {
            if (state.containsKey(key)) {
                String message = mapper.getMessageFromTuple(input);
                if (message.contains("error")) {
                    log.info("some error from worker, going to start cleanup procedure");
                    log.info("clear callback");
                    collector.emit(STREAM_TO_BOLT_COORDINATOR, new Values(
                            key, CoordinatorCommand.CANCEL_CALLBACK.name(),
                            0,
                            null
                    ));
                    log.info("pass message to nb");
                    collector.emit(STREAM_HUB_BOLT_TO_NB, new Values(
                            mapper.getKeyFromTuple(input),
                            "error in worker"));
                    state.remove(key);
                } else {
                    Integer op = state.get(key);
                    op -= 1;
                    if (op == 0) {
                        log.info("clear callback");
                        collector.emit(STREAM_TO_BOLT_COORDINATOR, new Values(
                                key, CoordinatorCommand.CANCEL_CALLBACK.name(),
                                0,
                                null
                        ));
                        log.info("pass message to nb");
                        collector.emit(STREAM_HUB_BOLT_TO_NB, new Values(
                                mapper.getKeyFromTuple(input),
                                "all operation is successful"));
                        state.remove(key);
                    } else {
                        state.put(key, op);
                    }
                }
            } else {
                log.error("missed field grouping");
            }
        } else if (input.getSourceComponent().equals(BOLT_COORDINATOR)) {
            if (state.containsKey(key)) {
                log.info("timeout callback received. going to start cleanup procedure");

                log.info("pass message to nb");
                collector.emit(STREAM_HUB_BOLT_TO_NB, new Values(
                        mapper.getKeyFromTuple(input),
                        "timeout error in hub"));
            }
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declareStream(STREAM_TO_BOLT_COORDINATOR, new Fields("key", "command", "timeout", "context"));

        Fields fields = new Fields(StormToKafkaTranslator.BOLT_KEY,
                StormToKafkaTranslator.BOLT_MESSAGE);

        declarer.declareStream(STREAM_HUB_BOLT_TO_WORKER_BOLT, fields);
        declarer.declareStream(STREAM_HUB_BOLT_TO_NB, fields);
    }
}