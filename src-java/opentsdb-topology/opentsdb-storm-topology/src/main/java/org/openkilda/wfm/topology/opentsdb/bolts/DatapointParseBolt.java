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

package org.openkilda.wfm.topology.opentsdb.bolts;

import static org.openkilda.wfm.share.zk.ZooKeeperSpout.FIELD_ID_LIFECYCLE_EVENT;

import org.openkilda.bluegreen.LifecycleEvent;
import org.openkilda.bluegreen.Signal;
import org.openkilda.messaging.info.Datapoint;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.wfm.share.zk.ZkStreams;
import org.openkilda.wfm.share.zk.ZooKeeperBolt;
import org.openkilda.wfm.share.zk.ZooKeeperSpout;
import org.openkilda.wfm.topology.utils.MessageKafkaTranslator;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DatapointParseBolt extends BaseRichBolt {
    private static final Logger LOGGER = LoggerFactory.getLogger(DatapointParseBolt.class);
    private transient OutputCollector collector;

    // True for testing
    private boolean active = true;

    @Override
    public void prepare(Map map, TopologyContext topologyContext, OutputCollector collector) {
        this.collector = collector;
    }

    @Override
    public void execute(Tuple tuple) {
        if (ZooKeeperSpout.SPOUT_ID.equals(tuple.getSourceComponent())) {
            LifecycleEvent event = (LifecycleEvent) tuple.getValueByField(FIELD_ID_LIFECYCLE_EVENT);
            handleLifeCycleEvent(tuple, event);
            collector.ack(tuple);
        } else if (active) {
            InfoData data = (InfoData) tuple.getValueByField(MessageKafkaTranslator.FIELD_ID_PAYLOAD);
            LOGGER.debug("Processing datapoint: {}", data);
            try {
                if (data instanceof Datapoint) {
                    Datapoint datapoint = (Datapoint) data;
                    List<Object> stream = Stream.of(datapoint.simpleHashCode(), datapoint)
                            .collect(Collectors.toList());
                    collector.emit(stream);
                } else {
                    LOGGER.error("Unhandled input tuple from {} with data {}", getClass().getName(), data);
                }
            } catch (Exception e) {
                LOGGER.error("Failed process data: {}", data, e);
            } finally {
                collector.ack(tuple);
            }
        } else {
            LOGGER.debug("DatapointParseBolt is inactive");
            collector.ack(tuple);
        }
    }

    protected void handleLifeCycleEvent(Tuple tuple, LifecycleEvent event) {
        if (Signal.START.equals(event.getSignal())) {
            active = true;
            collector.emit(ZkStreams.ZK.toString(), tuple, new Values(event, UUID.randomUUID()));
        } else if (Signal.SHUTDOWN.equals(event.getSignal())) {
            active = false;
            collector.emit(ZkStreams.ZK.toString(), tuple, new Values(event, UUID.randomUUID()));
        } else {
            LOGGER.error("Unsupported signal received: {}", event.getSignal());
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields("hash", "datapoint"));
        declarer.declareStream(ZkStreams.ZK.toString(), new Fields(ZooKeeperBolt.FIELD_ID_STATE,
                ZooKeeperBolt.FIELD_ID_CONTEXT));
    }
}
