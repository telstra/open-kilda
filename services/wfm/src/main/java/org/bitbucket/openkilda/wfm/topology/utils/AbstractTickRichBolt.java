package org.bitbucket.openkilda.wfm.topology.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.storm.Config;
import org.apache.storm.Constants;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Tuple;

import java.util.Map;

/**
 * A base class for Bolts interested in doing TickTuples.
 */
public abstract class AbstractTickRichBolt extends BaseRichBolt {

    private static final Logger logger = LogManager.getLogger(AbstractTickRichBolt.class);
    protected OutputCollector _collector;
    private Integer emitFrequency;

    public AbstractTickRichBolt() {
        emitFrequency = 1; // every second
    }

    public AbstractTickRichBolt(Integer frequency) {
        emitFrequency = frequency;
    }

    /*
     * Configure frequency of tick tuples for this bolt. This delivers a 'tick' tuple on a specific
     * interval, which is used to trigger certain actions
     */
    @Override
    public Map<String, Object> getComponentConfiguration() {
        Config conf = new Config();
        conf.put(Config.TOPOLOGY_TICK_TUPLE_FREQ_SECS, emitFrequency);
        return conf;
    }

    protected boolean isTickTuple(Tuple tuple) {
        return (tuple.getSourceComponent().equals(Constants.SYSTEM_COMPONENT_ID)
                && tuple.getSourceStreamId().equals(Constants.SYSTEM_TICK_STREAM_ID));
    }

    @Override
    public void prepare(Map conf, TopologyContext context, OutputCollector collector) {
        _collector = collector;
    }

    //execute is called to process tuples
    @Override
    public void execute(Tuple tuple) {
        //If it's a tick tuple, emit all words and counts
        if (isTickTuple(tuple)) {
            doTick(tuple);
        } else {
            doWork(tuple);
        }
    }

    protected abstract void doTick(Tuple tuple);

    protected abstract void doWork(Tuple tuple);

}
