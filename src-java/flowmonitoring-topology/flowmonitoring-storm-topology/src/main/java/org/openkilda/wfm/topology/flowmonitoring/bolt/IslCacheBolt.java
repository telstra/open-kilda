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

package org.openkilda.wfm.topology.flowmonitoring.bolt;

import static org.openkilda.wfm.topology.flowmonitoring.FlowMonitoringTopology.Stream.ISL_UPDATE_STREAM_ID;
import static org.openkilda.wfm.topology.flowmonitoring.bolt.FlowCacheBolt.FLOW_ID_FIELD;
import static org.openkilda.wfm.topology.flowmonitoring.bolt.FlowCacheBolt.LATENCY_FIELD;
import static org.openkilda.wfm.topology.flowmonitoring.bolt.FlowCacheBolt.LINK_FIELD;
import static org.openkilda.wfm.topology.flowmonitoring.bolt.FlowCacheBolt.REQUEST_ID_FIELD;
import static org.openkilda.wfm.topology.flowmonitoring.bolt.IslDataSplitterBolt.INFO_DATA_FIELD;

import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.event.IslChangedInfoData;
import org.openkilda.messaging.info.event.IslOneWayLatency;
import org.openkilda.messaging.info.event.IslRoundTripLatency;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.context.PersistenceContextRequired;
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
import java.time.Duration;

public class IslCacheBolt extends AbstractBolt {

    private PersistenceManager persistenceManager;
    private Duration islRttLatencyExpiration;

    private transient IslCacheService islCacheService;

    public IslCacheBolt(PersistenceManager persistenceManager, Duration islRttLatencyExpiration,
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
        if (ComponentId.ISL_SPLITTER_BOLT.name().equals(input.getSourceComponent())) {
            InfoData data = pullValue(input, INFO_DATA_FIELD, InfoData.class);
            if (ISL_UPDATE_STREAM_ID.name().equals(input.getSourceStreamId())) {
                if (data instanceof IslChangedInfoData) {
                    islCacheService.handleIslChangedData((IslChangedInfoData) data);
                } else {
                    unhandledInput(input);
                }
            } else if (data instanceof IslOneWayLatency) {
                islCacheService.handleOneWayLatency((IslOneWayLatency) data);
            } else if (data instanceof IslRoundTripLatency) {
                islCacheService.handleRoundTripLatency((IslRoundTripLatency) data);
            } else {
                unhandledInput(input);
            }
            return;
        }

        if (ComponentId.FLOW_CACHE_BOLT.name().equals(input.getSourceComponent())) {
            String requestId = pullValue(input, REQUEST_ID_FIELD, String.class);
            String flowId = pullValue(input, FLOW_ID_FIELD, String.class);
            Link link = pullValue(input, LINK_FIELD, Link.class);

            Duration latency = islCacheService.getLatencyForLink(link);

            emit(input, new Values(requestId, flowId, link, latency, getCommandContext()));
        } else {
            unhandledInput(input);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields(REQUEST_ID_FIELD, FLOW_ID_FIELD, LINK_FIELD, LATENCY_FIELD, FIELD_ID_CONTEXT));
        declarer.declareStream(ZkStreams.ZK.toString(), new Fields(ZooKeeperBolt.FIELD_ID_STATE,
                ZooKeeperBolt.FIELD_ID_CONTEXT));
    }
}
