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

package org.openkilda.wfm.topology.discovery.storm.bolt;

import org.openkilda.wfm.share.bolt.AbstractTick;
import org.openkilda.wfm.topology.discovery.storm.ComponentId;

import com.google.common.base.Preconditions;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;

public class MonotonicTick extends AbstractTick {
    public static final String BOLT_ID = ComponentId.MONOTONIC_TICK.toString();

    public static final String STREAM_DISCOVERY_ID = "discovery.tick";
    public static final Fields STREAM_DISCOVERY_FIELDS = AbstractTick.STREAM_FIELDS;

    private static final int TICK_INTERVAL_SECONDS = 1;  // 1 is the minimum possible value

    private final int discoveryInterval;

    public MonotonicTick(int discoveryInterval) {
        super(TICK_INTERVAL_SECONDS);

        Preconditions.checkArgument(0 < discoveryInterval,
                "invalid discoveryInterval value %d < 1", discoveryInterval);
        this.discoveryInterval = discoveryInterval;
    }

    @Override
    protected void produceTick(Tuple input) {
        super.produceTick(input);

        produceDiscoveryTick(input);
    }

    private void produceDiscoveryTick(Tuple input) {
        if (isMultiplierTick(discoveryInterval)) {
            getOutput().emit(STREAM_DISCOVERY_ID, input, input.getValues());
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputManager) {
        super.declareOutputFields(outputManager);

        outputManager.declareStream(STREAM_DISCOVERY_ID, STREAM_DISCOVERY_FIELDS);
    }
}
