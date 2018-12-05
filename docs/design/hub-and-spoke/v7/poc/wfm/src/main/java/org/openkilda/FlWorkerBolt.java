package org.openkilda;

import org.openkilda.hubandspoke.WorkerBolt;

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
        log.info("pass message to fl");
        String key = mapper.getKeyFromTuple(input);
        collector.emit(streamWorkerBoltToExternal, new Values(
                key,
                mapper.getMessageFromTuple(input)));
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