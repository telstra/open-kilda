package org.bitbucket.openkilda.wfm;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * LoggerBolt - just dumps everything received to the log file.
 */
public class LoggerBolt extends BaseRichBolt {
    private OutputCollector _collector;

    public enum LEVEL {INFO,DEBUG,TRACE};
    public LEVEL level = LEVEL.DEBUG;

    private static Logger logger = LoggerFactory.getLogger(LoggerBolt.class);

    @Override
    public void prepare(Map conf, TopologyContext context, OutputCollector collector) {
        _collector = collector;
    }

    @Override
    public void execute(Tuple tuple) {
        switch (level) {
            case INFO:
                logger.info("\nfields: {}\nvalues: {}", tuple.getFields(), tuple.getValues());
                break;
            case TRACE:
                logger.trace("\nfields: {}\nvalues: {}", tuple.getFields(), tuple.getValues());
                break;
            default:
                logger.debug("\nfields: {}\nvalues: {}", tuple.getFields(), tuple.getValues());
        }
        _collector.ack(tuple);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {}
}



