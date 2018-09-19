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

package org.openkilda.wfm.topology.utils;

import org.apache.storm.kafka.spout.KafkaSpout;
import org.apache.storm.kafka.spout.KafkaSpoutConfig;
import org.apache.storm.spout.SpoutOutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;

public class LcmKafkaSpout<K, V> extends KafkaSpout<K, V> {
    private static final Logger logger = LoggerFactory.getLogger(LcmKafkaSpout.class);

    public static final String STREAM_ID_LCM = "LCM";
    public static final Fields STREAM_SCHEMA_LCM = new Fields("kind", "payload");

    private UUID syncEventId = null;
    private boolean isSyncDone = false;
    private String spoutId = null;

    public LcmKafkaSpout(KafkaSpoutConfig<K, V> kafkaSpoutConfig) {
        super(kafkaSpoutConfig);
    }

    @Override
    public void nextTuple() {
        if (isSyncDone) {
            logger.trace("Proxy .nextTuple() call to super object");
            super.nextTuple();
        } else if (syncEventId == null) {
            logger.info("Spout {} - sending LCM sync event", spoutId);

            syncEventId = UUID.randomUUID();
            Values event = new Values(LcmEvent.SYNC_REQUEST, null);
            collector.emit(STREAM_ID_LCM, event, syncEventId);
        } else {
            logger.debug("Spout {} - is in passive mode, waiting LCM sync to complete", spoutId);
        }
    }

    @Override
    public void ack(Object messageId) {
        if (matchSyncId(messageId)) {
            logger.info("Spout {} - LCM sync is over, switch into active mode", spoutId);
            syncEventId = null;
            isSyncDone = true;
            return;
        }

        super.ack(messageId);
    }

    @Override
    public void fail(Object messageId) {
        if (matchSyncId(messageId)) {
            logger.error("Spout {} - LCM sync event is not handled (reschedule sync event)", spoutId);
            syncEventId = null;
            return;
        }

        super.fail(messageId);
    }

    @Override
    public void open(Map conf, TopologyContext context, SpoutOutputCollector collector) {
        spoutId = context.getThisComponentId();

        super.open(conf, context, collector);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        super.declareOutputFields(declarer);
        declarer.declareStream(STREAM_ID_LCM, STREAM_SCHEMA_LCM);
    }

    private boolean matchSyncId(Object messageId) {
        boolean isMatch = false;
        if (syncEventId != null) {
            isMatch = syncEventId.equals(messageId);
        }
        return isMatch;
    }
}
