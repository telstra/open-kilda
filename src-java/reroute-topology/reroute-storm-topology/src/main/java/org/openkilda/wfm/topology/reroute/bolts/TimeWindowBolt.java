/* Copyright 2020 Telstra Open Source
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

package org.openkilda.wfm.topology.reroute.bolts;

import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.hubandspoke.CoordinatorSpout;
import org.openkilda.wfm.topology.reroute.service.ExtendableTimeWindow;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.Map;

public class TimeWindowBolt extends AbstractBolt {

    public static final String BOLT_ID_TIME_WINDOW = "time-window-bolt";
    public static final String STREAM_TIME_WINDOW_EVENT_ID = "time-window-event-stream";

    private long minDelay;
    private long maxDelay;
    private transient ExtendableTimeWindow extendableTimeWindow;

    public TimeWindowBolt(long minDelay, long maxDelay) {
        this.minDelay = minDelay;
        this.maxDelay = maxDelay;
    }

    @Override
    protected void handleInput(Tuple input) {
        if (CoordinatorSpout.ID.equals(input.getSourceComponent())) {
            if (extendableTimeWindow.isTimeToFlush()) {
                extendableTimeWindow.flush();
                getOutput().emit(new Values(getCommandContext()));
            }
        } else {
            extendableTimeWindow.registerEvent();
        }
    }

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        super.prepare(stormConf, context, collector);
        extendableTimeWindow = new ExtendableTimeWindow(minDelay, maxDelay);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields(FIELD_ID_CONTEXT));
    }

    @Override
    protected void init() {
        super.init();
        // Imitate flush window event on bolt startup to prevent data loss when this bolt is restarted
        getOutput().emit(new Values(new CommandContext()));
    }
}
