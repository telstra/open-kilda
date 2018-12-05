package org.openkilda.hubandspoke;

import static org.openkilda.Constants.STREAM_HUB_BOLT_TO_NB;
import static org.openkilda.Constants.STREAM_HUB_BOLT_TO_WORKER_BOLT;
import static org.openkilda.hubandspoke.Constants.BOLT_COORDINATOR;

import lombok.Builder;
import lombok.Value;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public abstract class HubBolt extends CoordinatorClient {

    private static final Logger log = LoggerFactory.getLogger(HubBolt.class);

    protected final String componentSpoutHub;
    protected final String componentBoltWorker;
    protected final String streamHubBoltToRequester;
    protected final String streamHubBoltToWorkerBolt;
    protected final Boolean autoAck;

    public HubBolt(Config config) {
        super(config.defaultTimeout);
        componentSpoutHub = config.componentSpoutHub;
        componentBoltWorker = config.componentBoltWorker;
        streamHubBoltToRequester = config.streamHubBoltToRequester;
        streamHubBoltToWorkerBolt = config.streamHubBoltToWorkerBolt;
        autoAck = config.autoAck;
    }

    protected abstract void processIncomeTuple(Tuple input);

    protected abstract void processWorkerResponseTuple(Tuple input);

    @Override
    public void execute(Tuple input) {
        if (autoAck) {
            collector.ack(input);
        }
        if (input.getSourceComponent().equals(componentSpoutHub)) {
            processIncomeTuple(input);
        } else if (input.getSourceComponent().equals(componentBoltWorker)) {
            processWorkerResponseTuple(input);
        } else if (input.getSourceComponent().equals(BOLT_COORDINATOR)) {
            processTimeoutTuple(input);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        super.declareOutputFields(declarer);
        declarer.declareStream(STREAM_HUB_BOLT_TO_WORKER_BOLT, StormToKafkaTranslator.FIELDS);
        declarer.declareStream(STREAM_HUB_BOLT_TO_NB, StormToKafkaTranslator.FIELDS);
    }

    public static @Value
    @Builder
    class Config {
        private String componentSpoutHub;
        private String componentBoltWorker;
        private String streamHubBoltToRequester;
        private String streamHubBoltToWorkerBolt;
        private Long defaultTimeout = 500L;
        private Boolean autoAck = true;
    }
}