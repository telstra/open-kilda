package org.openkilda;

import org.openkilda.hubandspoke.HubBolt;
import org.openkilda.model.FlCommand;
import org.openkilda.model.FlowCreate;

import lombok.extern.slf4j.Slf4j;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.io.IOException;
import java.util.Map;

@Slf4j
public class FlowCrudBolt extends HubBolt implements IFlowCrudCarrier {

    private FlowCrudService service;

    public FlowCrudBolt(Config config) {
        super(config);
    }

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        super.prepare(stormConf, context, collector);
        service = new FlowCrudService(this);
    }

    @Override
    protected void processIncomeTuple(Tuple input) {
        String key = mapper.getKeyFromTuple(input);
        try {
            FlowCreate flow = Utils.MAPPER.readValue(mapper.getMessageFromTuple(input), FlowCreate.class);
            service.handleFlowCreate(key, flow);
        } catch (IOException e) {
            log.error("can't read message", e);
        }
    }

    @Override
    protected void processWorkerResponseTuple(Tuple input) {
        String key = mapper.getKeyFromTuple(input);
        String message = mapper.getMessageFromTuple(input);
        service.handleAsyncResponseFromWorker(key, message);
    }

    @Override
    protected void processTimeoutTuple(Tuple input) {
        String key = mapper.getKeyFromTuple(input);
        service.handleTaskTimeout(key);
    }

    @Override
    public void installRule(String key, FlCommand command) {
        collector.emit(streamHubBoltToWorkerBolt, new Values(key, command));
    }

    @Override
    public void checkRule(String key, FlCommand command) {
        collector.emit(streamHubBoltToWorkerBolt, new Values(key, command));
    }

    @Override
    public void response(String key, String response) {
        collector.emit(streamHubBoltToRequester, new Values(key, response));
    }
}
