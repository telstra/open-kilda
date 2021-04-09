/* Copyright 2021 Telstra Open Source
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

package org.openkilda.wfm.topology.flowmonitoring.bolts;

import static org.openkilda.wfm.topology.flowmonitoring.FlowMonitoringTopology.Stream.ACTION_STREAM_ID;
import static org.openkilda.wfm.topology.flowmonitoring.bolts.FlowCacheBolt.FLOW_DIRECTION_FIELD;
import static org.openkilda.wfm.topology.flowmonitoring.bolts.FlowCacheBolt.FLOW_ID_FIELD;
import static org.openkilda.wfm.topology.flowmonitoring.bolts.FlowCacheBolt.FLOW_PATH_FIELD;
import static org.openkilda.wfm.topology.flowmonitoring.bolts.FlowCacheBolt.LATENCY_FIELD;
import static org.openkilda.wfm.topology.flowmonitoring.bolts.FlowCacheBolt.MAX_LATENCY_FIELD;
import static org.openkilda.wfm.topology.flowmonitoring.bolts.FlowCacheBolt.MAX_LATENCY_TIER_2_FIELD;
import static org.openkilda.wfm.topology.utils.KafkaRecordTranslator.FIELD_ID_PAYLOAD;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.event.IslChangedInfoData;
import org.openkilda.messaging.info.event.IslOneWayLatency;
import org.openkilda.messaging.info.event.IslRoundTripLatency;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.context.PersistenceContextRequired;
import org.openkilda.server42.messaging.FlowDirection;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.share.zk.ZkStreams;
import org.openkilda.wfm.share.zk.ZooKeeperBolt;
import org.openkilda.wfm.topology.flowmonitoring.FlowMonitoringTopology.ComponentId;
import org.openkilda.wfm.topology.flowmonitoring.model.Link;
import org.openkilda.wfm.topology.flowmonitoring.service.IslCacheService;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.time.Clock;
import java.util.List;

public class IslCacheBolt extends AbstractBolt {

    private PersistenceManager persistenceManager;
    private long islRttLatencyExpiration;

    private transient IslCacheService islCacheService;

    public IslCacheBolt(PersistenceManager persistenceManager, long islRttLatencyExpiration,
                        String lifeCycleEventSourceComponent) {
        super(lifeCycleEventSourceComponent);
        this.persistenceManager = persistenceManager;
        this.islRttLatencyExpiration = islRttLatencyExpiration;
    }

    @PersistenceContextRequired(requiresNew = true)
    protected void init() {
        islCacheService = new IslCacheService(persistenceManager, Clock.systemUTC(), islRttLatencyExpiration);
    }

    @Override
    protected void handleInput(Tuple input) throws PipelineException {
        if (!active) {
            return;
        }
        if (ComponentId.ISL_SPOUT.name().equals(input.getSourceComponent())) {
            Message message = pullValue(input, FIELD_ID_PAYLOAD, Message.class);

            if (message instanceof InfoMessage) {
                InfoData data = ((InfoMessage) message).getData();

                if (data instanceof IslChangedInfoData) {
                    islCacheService.handleIslChangedData((IslChangedInfoData) data);
                } else {
                    unhandledInput(input);
                }
            } else {
                unhandledInput(input);
            }
            return;
        }

        if (ComponentId.ISL_LATENCY_SPOUT.name().equals(input.getSourceComponent())) {
            Message message = pullValue(input, FIELD_ID_PAYLOAD, Message.class);

            if (message instanceof InfoMessage) {
                InfoData data = ((InfoMessage) message).getData();

                if (data instanceof IslOneWayLatency) {
                    islCacheService.handleOneWayLatency((IslOneWayLatency) data);
                } else if (data instanceof IslRoundTripLatency) {
                    islCacheService.handleRoundTripLatency((IslRoundTripLatency) data);
                } else {
                    unhandledInput(input);
                }
            } else {
                unhandledInput(input);
            }
            return;
        }

        if (ComponentId.FLOW_CACHE_BOLT.name().equals(input.getSourceComponent())) {
            String flowId = pullValue(input, FLOW_ID_FIELD, String.class);
            FlowDirection direction = pullValue(input, FLOW_DIRECTION_FIELD, FlowDirection.class);
            List<Link> flowPath = (List<Link>) pullValue(input, FLOW_PATH_FIELD, List.class);
            Long maxLatency = pullValue(input, MAX_LATENCY_FIELD, Long.class);
            Long maxLatencyTier2 = pullValue(input, MAX_LATENCY_TIER_2_FIELD, Long.class);

            long latency = islCacheService.calculateLatencyForPath(flowPath);

            emit(ACTION_STREAM_ID.name(), input, new Values(flowId, direction, latency, maxLatency, maxLatencyTier2,
                    getCommandContext()));
        } else {
            unhandledInput(input);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declareStream(ACTION_STREAM_ID.name(), new Fields(FLOW_ID_FIELD, FLOW_DIRECTION_FIELD,
                LATENCY_FIELD, MAX_LATENCY_FIELD, MAX_LATENCY_TIER_2_FIELD, FIELD_ID_CONTEXT));
        declarer.declareStream(ZkStreams.ZK.toString(), new Fields(ZooKeeperBolt.FIELD_ID_STATE,
                ZooKeeperBolt.FIELD_ID_CONTEXT));
    }
}
