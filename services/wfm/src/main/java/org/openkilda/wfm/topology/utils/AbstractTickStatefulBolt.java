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

import org.apache.storm.utils.TupleUtils;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.apache.storm.Config;
import org.apache.storm.Constants;
import org.apache.storm.state.State;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.base.BaseStatefulBolt;
import org.apache.storm.tuple.Tuple;

import java.util.Map;

/**
 * A base class for Bolts interested in doing TickTuples.
 */
public abstract class AbstractTickStatefulBolt<T extends State> extends BaseStatefulBolt<T> {

    private static final Logger logger = LoggerFactory.getLogger(AbstractTickStatefulBolt.class);
    protected OutputCollector _collector;
    /** emitFrequency is in seconds */
    private Integer emitFrequency;
    /** default is 1 second frequency */
    private static final int DEFAULT_FREQUENCY = 1;

    public AbstractTickStatefulBolt() {
        emitFrequency = DEFAULT_FREQUENCY;
    }

    /** @param frequency is in seconds */
    public AbstractTickStatefulBolt(Integer frequency) {
        emitFrequency = frequency;
    }

    public AbstractTickStatefulBolt withFrequency(Integer frequency){
        this.emitFrequency = frequency;
        return this;
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

    @Override
    public void prepare(Map conf, TopologyContext context, OutputCollector collector) {
        _collector = collector;
    }

    //execute is called to process tuples
    @Override
    public void execute(Tuple tuple) {
        if (TupleUtils.isTick(tuple)) {
            doTick(tuple);
        } else {
            doWork(tuple);
        }
    }

    protected abstract void doTick(Tuple tuple);

    protected abstract void doWork(Tuple tuple);

}
