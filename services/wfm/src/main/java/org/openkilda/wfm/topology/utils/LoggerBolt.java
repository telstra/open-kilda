/* Copyright 2017 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.wfm.topology.utils;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Tuple;

import java.util.Map;

/**
 * LoggerBolt - just dumps everything received to the log file.
 */
public class LoggerBolt extends BaseRichBolt {

    private static Logger logger = LogManager.getLogger(LoggerBolt.class);
    public Level level = Level.DEBUG;
    public String watermark = "";
    private OutputCollector _collector;

    public LoggerBolt withLevel(Level level) {
        this.level = level;
        return this;
    }

    public LoggerBolt withWatermark(String watermark) {
        this.watermark = watermark;
        return this;
    }

    @Override
    public void prepare(Map conf, TopologyContext context, OutputCollector collector) {
        _collector = collector;
    }

    @Override
    public void execute(Tuple tuple) {
        System.out.println("this = " + this);
        logger.log(level, "\n{}: fields: {} :: values: {}",
                watermark, tuple.getFields(), tuple.getValues());
        _collector.ack(tuple);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
    }
}



