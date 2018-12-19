package org.openkilda.hubandspoke;

import static org.openkilda.hubandspoke.Constants.STREAM_TO_BOLT_COORDINATOR;

import org.openkilda.hubandspoke.CoordinatorBolt.CoordinatorCommand;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.Map;

public abstract class CoordinatorClient extends BaseRichBolt {

    protected transient OutputCollector collector;
    protected StormToKafkaTranslator mapper = new StormToKafkaTranslator();
    private Long defaultTimeout;


    public CoordinatorClient(Long defaultTimeout) {
        this.defaultTimeout = defaultTimeout;
    }

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        this.collector = collector;
    }

    public void cancelCallback(String key) {
        collector.emit(STREAM_TO_BOLT_COORDINATOR, new Values(
                key, CoordinatorCommand.CANCEL_CALLBACK.name(),
                0,
                null
        ));
    }

    public void registerCallback(String key) {
        registerCallback(key, defaultTimeout);
    }

    public void registerCallback(String key, long timeout) {
        collector.emit(STREAM_TO_BOLT_COORDINATOR, new Values(
                key, CoordinatorCommand.REQUEST_CALLBACK.name(),
                timeout,
                key
        ));
    }

    public void declareCoordinatorStream(OutputFieldsDeclarer declarer) {
        declarer.declareStream(STREAM_TO_BOLT_COORDINATOR, new Fields("key", "command", "timeout", "context"));
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declareCoordinatorStream(declarer);
    }

    protected void processTimeoutTuple(Tuple input) {
    }
}
