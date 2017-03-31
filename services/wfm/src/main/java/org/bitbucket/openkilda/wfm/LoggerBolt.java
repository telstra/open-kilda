package org.bitbucket.openkilda.wfm;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Tuple;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Level;

import java.util.Map;

/**
 * LoggerBolt - just dumps everything received to the log file.
 */
public class LoggerBolt extends BaseRichBolt {

    private OutputCollector _collector;
    private static Logger logger = LogManager.getLogger(LoggerBolt.class);
    public Level level = Level.DEBUG;
    public String watermark = "";

    public LoggerBolt withLevel(Level level){
        this.level = level;
        return this;
    }

    public LoggerBolt withWatermark(String watermark){
        this.watermark = watermark;
        return this;
    }

    @Override
    public void prepare(Map conf, TopologyContext context, OutputCollector collector) {
        _collector = collector;
    }

    @Override
    public void execute(Tuple tuple) {
        logger.log(level, "\n{}: fields: {} \n{}: values: {}",
                watermark, tuple.getFields(), watermark, tuple.getValues());
        _collector.ack(tuple);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {}
}



