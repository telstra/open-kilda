package org.openkilda;

import org.openkilda.hubandspoke.HubBolt;
import org.openkilda.model.FlowCreate;
import org.openkilda.model.FlowCreate.Error;

import lombok.extern.slf4j.Slf4j;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class FlowCrudBolt extends HubBolt {

    private Map<String, Integer> state = new HashMap<>();

    public FlowCrudBolt(Config config) {
        super(config);
    }

    @Override
    protected void processTimeoutTuple(Tuple input) {
        String key = mapper.getKeyFromTuple(input);
        if (state.containsKey(key)) {
            log.info("timeout callback received. going to start cleanup procedure");
            log.info("pass message to nb");
            collector.emit(streamHubBoltToRequester, new Values(
                    mapper.getKeyFromTuple(input),
                    "timeout error in hub"));
        }
    }

    @Override
    protected void processIncomeTuple(Tuple input) {
        String key = mapper.getKeyFromTuple(input);
        registerCallback(key);
        try {
            FlowCreate flowCreate = Utils.MAPPER.readValue(mapper.getMessageFromTuple(input), FlowCreate.class);
            int hops = flowCreate.getLength();
            FlowCreate.Error error = flowCreate.getError();
            state.put(key, hops);

            if (error == null) {
                log.info("pass messages to worker");
                for (int i = 0; i < hops; ++i) {
                    collector.emit(streamHubBoltToWorkerBolt, new Values(
                            String.format("%s-%d", mapper.getKeyFromTuple(input), i),
                            String.format("operation %d", i)));
                }
            } else if (error == Error.IN_WORKER) {
                log.info("pass message with error to worker");
                collector.emit(streamHubBoltToWorkerBolt, new Values(
                        String.format("%s-%d", mapper.getKeyFromTuple(input), -1),
                        String.format("operation %d", -1)));
            } else if (error == Error.IN_HUB) {
                log.info("error in hub. do nothing and waiting for callback");
            }
        } catch (IOException e) {
            log.error("can't read message", e);
        }
    }

    @Override
    protected void processWorkerResponseTuple(Tuple input) {
        String key = mapper.getKeyFromTuple(input);
        if (state.containsKey(key)) {
            String message = mapper.getMessageFromTuple(input);
            if (message.contains("error")) {
                log.info("some error from worker, going to start cleanup procedure");
                log.info("clear callback");
                cancelCallback(key);
                log.info("pass message to nb");
                collector.emit(streamHubBoltToRequester, new Values(
                        mapper.getKeyFromTuple(input),
                        "error in worker"));
                state.remove(key);
            } else {
                Integer op = state.get(key);
                op -= 1;
                if (op == 0) {
                    log.info("clear callback");
                    cancelCallback(key);
                    log.info("pass message to nb");
                    collector.emit(streamHubBoltToRequester, new Values(
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
    }
}
