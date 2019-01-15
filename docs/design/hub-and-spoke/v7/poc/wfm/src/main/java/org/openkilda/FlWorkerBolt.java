package org.openkilda;

import org.openkilda.hubandspoke.StormToKafkaTranslator;
import org.openkilda.hubandspoke.WorkerBolt;
import org.openkilda.model.FlCommand;
import org.openkilda.model.FlowCreateError;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

@Slf4j
public class FlWorkerBolt extends WorkerBolt {

    public FlWorkerBolt(Config config) {
        super(config);
    }

    @Override
    protected void processHubTuple(Tuple input) {
        String key = mapper.getKeyFromTuple(input);
        FlCommand command = (FlCommand) input.getValueByField(StormToKafkaTranslator.BOLT_MESSAGE);
        if (command.getError() == FlowCreateError.IN_WORKER) {
            log.info("some error in worker, do nothing wait for callback");
        } else {
            log.info("pass message to fl");
            try {
                collector.emit(streamWorkerBoltToExternal, new Values(
                        key,
                        Utils.MAPPER.writeValueAsString(command)));
            } catch (JsonProcessingException e) {
                log.error("can't write message", e);
            }
        }
    }

    @Override
    protected void processAsyncResponseTuple(Tuple input) {
        log.info("pass response to hub");
        String key = mapper.getKeyFromTuple(input);
        String parentKey = key.substring(0, 32);
        Tuple task = tasks.remove(key);
        collector.emitDirect(task.getSourceTask(), streamWorkerBoltToHubBolt, new Values(
                parentKey,
                mapper.getMessageFromTuple(input)));
    }

    @Override
    protected void processTimeoutTuple(Tuple input) {
        log.info("timeout callback received. going to cleanup");
        log.info("pass failed response to hub");
        String key = mapper.getKeyFromTuple(input);
        String parentKey = key.substring(0, 32);
        Tuple task = tasks.remove(key);
        collector.emitDirect(task.getSourceTask(), streamWorkerBoltToHubBolt, new Values(
                parentKey,
                "some error"));
    }
}