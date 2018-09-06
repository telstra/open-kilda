/* Copyright 2018 Telstra Open Source
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

package org.openkilda.wfm.topology.ping.bolt;

import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.bolt.AbstractTick;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

public class MonotonicTick extends AbstractTick {
    public static final String BOLT_ID = ComponentId.MONOTONIC_TICK.toString();

    public static final String FIELD_ID_TICK = "tick";

    public static final String STREAM_PING_ID = "ping.tick";
    public static final Fields STREAM_PING_FIELDS = new Fields(FIELD_ID_TICK, FIELD_ID_CONTEXT);

    private final int pingInterval;
    private int pingSequenceIndex = 0;

    public MonotonicTick(int pingInterval) {
        // TODO(surabujin): switch to subsecond frequency
        super(1);

        this.pingInterval = pingInterval;
        if (this.pingInterval < 1) {
            throw new IllegalArgumentException(String.format("Invalid pingInterval value %d < 1", this.pingInterval));
        }
    }

    @Override
    protected void produceTick(Tuple input) {
        super.produceTick(input);

        pingTick(input);
    }

    private void pingTick(Tuple input) {
        if (++pingSequenceIndex <= pingInterval) {
            return;
        }

        pingSequenceIndex = 0;

        Values output = new Values(input.getValue(0), new CommandContext());
        getOutput().emit(STREAM_PING_ID, input, output);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputManager) {
        super.declareOutputFields(outputManager);
        outputManager.declareStream(STREAM_PING_ID, STREAM_PING_FIELDS);
    }
}
