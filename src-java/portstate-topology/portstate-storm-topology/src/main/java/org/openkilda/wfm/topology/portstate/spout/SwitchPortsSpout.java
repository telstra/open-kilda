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

package org.openkilda.wfm.topology.portstate.spout;

import static java.lang.String.format;
import static org.openkilda.wfm.AbstractBolt.FIELD_ID_CONTEXT;

import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.discovery.PortsCommandData;
import org.openkilda.wfm.share.bolt.KafkaEncoder;

import org.apache.storm.spout.SpoutOutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichSpout;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;

public class SwitchPortsSpout extends BaseRichSpout {

    private static final Logger logger = LoggerFactory.getLogger(SwitchPortsSpout.class);
    private static final String CRON_TUPLE = "cron.tuple";
    private static final int DEFAULT_FREQUENCY = 600;
    private static final String REQUESTER = SwitchPortsSpout.class.getSimpleName();
    private final long frequency;
    private SpoutOutputCollector collector;
    private long lastTickTime = 0;

    public SwitchPortsSpout() {
        this(DEFAULT_FREQUENCY);
    }

    public SwitchPortsSpout(int frequency) {
        this.frequency = frequency * 1000L;
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    @Override
    public void open(Map map, TopologyContext context, SpoutOutputCollector collector) {
        this.collector = collector;
    }

    @Override
    public void nextTuple() {
        final long now = now();
        if (now - lastTickTime > frequency) {
            CommandData data = new PortsCommandData(REQUESTER);
            logger.debug("emitting PortsCommandData: {}", data);

            String correlationId = format("SwitchPortsSpout-%s", UUID.randomUUID().toString());
            collector.emit(new Values(correlationId, data, correlationId));

            if (now - lastTickTime > frequency * 2) {
                logger.warn("long tick for PortsCommandData - {}ms", now - lastTickTime);
            }

            lastTickTime = now;
        }
        org.apache.storm.utils.Utils.sleep(1);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields(KafkaEncoder.FIELD_ID_KEY, KafkaEncoder.FIELD_ID_PAYLOAD, FIELD_ID_CONTEXT));
    }
}
