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

package org.openkilda.wfm.topology.statsrouter.bolts;

import static org.openkilda.wfm.topology.AbstractTopology.fieldMessage;
import static org.openkilda.wfm.topology.statsrouter.StatsRouterStreamType.MGMT_REQUEST;
import static org.openkilda.wfm.topology.statsrouter.StatsRouterStreamType.STATS_REQUEST;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.wfm.topology.statsrouter.service.MessageSender;
import org.openkilda.wfm.topology.statsrouter.service.StatsRouterService;
import org.openkilda.wfm.topology.utils.AbstractTickRichBolt;
import org.openkilda.wfm.topology.utils.MessageKafkaTranslator;

import lombok.extern.slf4j.Slf4j;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.Map;

@Slf4j
public class StatsRouterBolt extends AbstractTickRichBolt implements MessageSender {
    private transient Tuple currentTuple;
    private transient StatsRouterService statsRouterService;
    private int timeout;

    public StatsRouterBolt(int interval, int timeout) {
        super(interval);
        this.timeout = timeout;
    }

    @Override
    public void prepare(Map conf, TopologyContext context, OutputCollector collector) {
        super.prepare(conf, context, collector);
        statsRouterService = new StatsRouterService(timeout, this);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declareStream(MGMT_REQUEST.name(), fieldMessage);
        declarer.declareStream(STATS_REQUEST.name(), fieldMessage);
    }

    @Override
    public void sendToMgmt(Message message) {
        Values values = new Values(message);
        outputCollector.emit(MGMT_REQUEST.name(), currentTuple, values);
    }

    @Override
    public void sendToStats(Message message) {
        Values values = new Values(message);
        outputCollector.emit(STATS_REQUEST.name(), currentTuple, values);
    }

    @Override
    protected void doTick(Tuple tuple) {
        try {
            currentTuple = tuple;
            statsRouterService.handleTick();
        } finally {
            outputCollector.ack(tuple);
        }
    }

    @Override
    protected void doWork(Tuple tuple) {
        try {
            currentTuple = tuple;
            Object message = tuple.getValueByField(MessageKafkaTranslator.FIELD_ID_PAYLOAD);
            if (message instanceof CommandMessage) {
                statsRouterService.handleStatsRequest((CommandMessage) message);
            } else if (message instanceof InfoMessage) {
                statsRouterService.handleListSwitchesResponse((InfoMessage) message);
            }
        } finally {
            outputCollector.ack(tuple);
        }
    }
}
