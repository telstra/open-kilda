package org.openkilda.hubandspoke;

import static org.openkilda.hubandspoke.Constants.BOLT_COORDINATOR;

import lombok.Builder;
import lombok.Value;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;


public abstract class WorkerBolt extends CoordinatorClient {

    private static final Logger log = LoggerFactory.getLogger(WorkerBolt.class);

    protected final String streamWorkerBoltToHubBolt;
    protected final String streamWorkerBoltToExternal;
    protected final String componentBoltHub;
    protected final String componentSpoutWorker;
    protected final Boolean autoAck;

    protected Map<String, Tuple> tasks = new HashMap<>();

    public WorkerBolt(Config config) {
        super(config.defaultTimeout);
        streamWorkerBoltToHubBolt = config.streamWorkerBoltToHubBolt;
        streamWorkerBoltToExternal = config.streamWorkerBoltToExternal;
        componentBoltHub = config.componentBoltHub;
        componentSpoutWorker = config.componentSpoutWorker;
        autoAck = config.autoAck;
    }


    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        super.prepare(stormConf, context, collector);
    }

    @Override
    public void execute(Tuple input) {
        if (autoAck) {
            collector.ack(input);
        }
        String key = mapper.getKeyFromTuple(input);
        String sender = input.getSourceComponent();

        if (sender.equals(componentBoltHub)) {
            registerCallback(key);
            tasks.put(key, input);
            processHubTuple(input);
        } else if (sender.equals(componentSpoutWorker)) {
            if (tasks.containsKey(key)) {
                cancelCallback(key);
                processAsyncResponseTuple(input);
            } else {
                log.error("missed task in worker bolt");
            }
        } else if (sender.equals(BOLT_COORDINATOR)) {
            if (tasks.containsKey(key)) {
                processTimeoutTuple(input);
            } else {
                log.error("missed task in worker bolt");
            }
        }
    }

    protected abstract void processHubTuple(Tuple input);

    protected abstract void processAsyncResponseTuple(Tuple input);

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        super.declareOutputFields(declarer);
        declarer.declareStream(streamWorkerBoltToExternal, StormToKafkaTranslator.FIELDS);
        declarer.declareStream(streamWorkerBoltToHubBolt, true, StormToKafkaTranslator.FIELDS);
    }

    public static @Value
    @Builder
    class Config {
        private String streamWorkerBoltToHubBolt;
        private String streamWorkerBoltToExternal;
        private String componentBoltHub;
        private String componentSpoutWorker;
        private Long defaultTimeout = 100L;
        private Boolean autoAck = true;
    }
}