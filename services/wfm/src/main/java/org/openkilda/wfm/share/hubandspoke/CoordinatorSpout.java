/* Copyright 2019 Telstra Open Source
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

package org.openkilda.wfm.share.hubandspoke;

import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.CommandContext;

import org.apache.storm.spout.SpoutOutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichSpout;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Values;

import java.util.Map;

/**
 * Simple spout that sends tuple with current time every millisecond.
 */
public final class CoordinatorSpout extends BaseRichSpout {
    public static final String ID = "coordinator.spout";

    public static final String FIELD_ID_TIME_MS = "time.ms";
    public static final String FIELD_ID_CONTEXT = AbstractBolt.FIELD_ID_CONTEXT;

    private SpoutOutputCollector collector;

    @Override
    public void open(Map map, TopologyContext context, SpoutOutputCollector collector) {
        this.collector = collector;
    }

    @Override
    public void nextTuple() {
        collector.emit(new Values(System.currentTimeMillis(), new CommandContext()));
        org.apache.storm.utils.Utils.sleep(100L);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields(FIELD_ID_TIME_MS, FIELD_ID_CONTEXT));
    }
}
