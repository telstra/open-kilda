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

package org.openkilda.wfm.topology.isllatency.bolts;

import static org.openkilda.wfm.topology.isllatency.IslLatencyTopology.CACHE_DATA_FIELD;
import static org.openkilda.wfm.topology.isllatency.IslLatencyTopology.ISL_GROUPING_FIELD;
import static org.openkilda.wfm.topology.isllatency.IslLatencyTopology.ISL_STATUS_SPOUT_ID;
import static org.openkilda.wfm.topology.isllatency.IslLatencyTopology.LATENCY_DATA_FIELD;
import static org.openkilda.wfm.topology.isllatency.IslLatencyTopology.ROUTER_BOLT_ID;
import static org.openkilda.wfm.topology.isllatency.IslLatencyTopology.TIMESTAMP_FIELD;

import org.openkilda.messaging.info.event.IslRoundTripLatency;
import org.openkilda.messaging.info.event.IslStatusUpdateNotification;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.share.model.Endpoint;
import org.openkilda.wfm.share.model.IslReference;
import org.openkilda.wfm.topology.isllatency.model.StreamType;
import org.openkilda.wfm.topology.isllatency.service.CacheCarrier;
import org.openkilda.wfm.topology.isllatency.service.CacheService;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;


public class CacheBolt extends AbstractBolt implements CacheCarrier {
    private final PersistenceManager persistenceManager;
    private transient CacheService cacheService;

    public CacheBolt(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    @Override
    public void init() {
        cacheService = new CacheService(this, persistenceManager.getRepositoryFactory());
    }

    @Override
    protected void handleInput(Tuple tuple) throws PipelineException {

        if (ISL_STATUS_SPOUT_ID.equals(tuple.getSourceComponent())) {
            IslStatusUpdateNotification notification =
                    pullValue(tuple, LATENCY_DATA_FIELD, IslStatusUpdateNotification.class);
            cacheService.handleUpdateCache(notification);
        } else if (ROUTER_BOLT_ID.equals(tuple.getSourceComponent())) {
            IslRoundTripLatency roundTripLatency = pullValue(tuple, LATENCY_DATA_FIELD, IslRoundTripLatency.class);
            long timestamp = tuple.getLongByField(TIMESTAMP_FIELD);
            cacheService.handleGetDataFromCache(tuple, roundTripLatency, timestamp);
        } else {
            unhandledInput(tuple);
        }
    }

    @Override
    public void emitCachedData(Tuple tuple, IslRoundTripLatency data, Endpoint destination, long timestamp) {
        IslReference islReference = new IslReference(
                Endpoint.of(data.getSrcSwitchId(), data.getSrcPortNo()), destination);

        Values values = new Values(islReference, data, destination, timestamp, getCommandContext());
        getOutput().emit(StreamType.STATS.toString(), tuple, values);
        getOutput().emit(StreamType.LATENCY.toString(), tuple, values);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        Fields fields = new Fields(
                ISL_GROUPING_FIELD, LATENCY_DATA_FIELD, CACHE_DATA_FIELD, TIMESTAMP_FIELD, FIELD_ID_CONTEXT);
        declarer.declareStream(StreamType.STATS.toString(), fields);
        declarer.declareStream(StreamType.LATENCY.toString(), fields);
    }
}
