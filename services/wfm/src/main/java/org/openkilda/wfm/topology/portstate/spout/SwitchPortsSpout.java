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

import org.openkilda.messaging.Destination;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.Utils;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.discovery.PortsCommandData;

import com.fasterxml.jackson.core.JsonProcessingException;
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
            Message message = buildPortsCommand();
            logger.debug("emitting PortsCommandData: {}", message);
            try {
                Values values = new Values(null, Utils.MAPPER.writeValueAsString(message));
                collector.emit(values);
            } catch (JsonProcessingException e) {
                logger.error("Error on json serialization", e);
            }

            if (now - lastTickTime > frequency * 2) {
                logger.warn("long tick for PortsCommandData - {}ms", now - lastTickTime);
            }

            lastTickTime = now;
        }
        org.apache.storm.utils.Utils.sleep(1);
    }

    private Message buildPortsCommand() {
        String correlationId = format("SwitchPortsSpout-%s", UUID.randomUUID().toString());
        return new CommandMessage(new PortsCommandData(REQUESTER), now(), correlationId,
                Destination.CONTROLLER);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields("key", "message"));
    }
}
