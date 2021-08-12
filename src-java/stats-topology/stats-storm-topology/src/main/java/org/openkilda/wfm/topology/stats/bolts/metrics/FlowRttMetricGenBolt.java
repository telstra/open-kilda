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

package org.openkilda.wfm.topology.stats.bolts.metrics;

import static org.openkilda.wfm.share.utils.TimestampHelper.noviflowTimestamp;

import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.stats.FlowRttStatsData;
import org.openkilda.wfm.share.zk.ZkStreams;
import org.openkilda.wfm.share.zk.ZooKeeperBolt;
import org.openkilda.wfm.topology.utils.KafkaRecordTranslator;

import com.google.common.collect.ImmutableMap;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class FlowRttMetricGenBolt extends MetricGenBolt {
    public static final String ZOOKEEPER_STREAM = ZkStreams.ZK.toString();

    public FlowRttMetricGenBolt(String metricPrefix, String lifeCycleEventSourceComponent) {
        super(metricPrefix, lifeCycleEventSourceComponent);
    }

    @Override
    protected void handleInput(Tuple input) throws Exception {
        if (active) {
            InfoMessage message = (InfoMessage) input.getValueByField(KafkaRecordTranslator.FIELD_ID_PAYLOAD);
            FlowRttStatsData data = (FlowRttStatsData) message.getData();
            Map<String, String> tags = ImmutableMap.of(
                    "direction", data.getDirection(),
                    "flowid", data.getFlowId(),
                    "origin", "server42"
            );

            long t0 = noviflowTimestamp(data.getT0());
            long t1 = noviflowTimestamp(data.getT1());

            // We decided to use t1 time as a timestamp for Datapoint.
            long timestamp = TimeUnit.NANOSECONDS.toMillis(t1);

            emitMetric("flow.rtt", timestamp, t1 - t0, tags);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        super.declareOutputFields(declarer);
        declarer.declareStream(ZOOKEEPER_STREAM, new Fields(ZooKeeperBolt.FIELD_ID_STATE,
                ZooKeeperBolt.FIELD_ID_CONTEXT));
    }
}
