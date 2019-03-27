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

package org.openkilda.wfm.topology.stats.bolts;

import static org.openkilda.wfm.topology.stats.StatsStreamType.STATS_REQUEST;

import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.stats.StatsRequest;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.utils.AbstractTickRichBolt;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.UUID;

public class StatsRequesterBolt  extends AbstractTickRichBolt {

    public StatsRequesterBolt(Integer frequency) {
        super(frequency);
    }

    @Override
    protected void doTick(Tuple tuple) {
        StatsRequest statsRequestData = new StatsRequest();
        CommandMessage statsRequest = new CommandMessage(statsRequestData,
                System.currentTimeMillis(), UUID.randomUUID().toString());
        Values values = new Values(statsRequest);
        outputCollector.emit(STATS_REQUEST.name(), tuple, values);
    }

    @Override
    protected void doWork(Tuple tuple) {

    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declareStream(STATS_REQUEST.name(), new Fields(AbstractTopology.MESSAGE_FIELD));
    }
}
