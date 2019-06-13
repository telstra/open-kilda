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
import static org.openkilda.wfm.topology.isllatency.IslLatencyTopology.ISL_KEY_FIELD;
import static org.openkilda.wfm.topology.isllatency.IslLatencyTopology.ISL_STATUS_SPOUT_ID;
import static org.openkilda.wfm.topology.isllatency.IslLatencyTopology.LATENCY_DATA_FIELD;
import static org.openkilda.wfm.topology.isllatency.IslLatencyTopology.ROUTER_BOLT_ID;
import static org.openkilda.wfm.topology.isllatency.IslLatencyTopology.TIMESTAMP_FIELD;

import org.openkilda.messaging.info.event.IslMoveNotification;
import org.openkilda.messaging.info.event.IslRoundTripLatency;
import org.openkilda.model.Isl;
import org.openkilda.model.IslStatus;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.topology.isllatency.Utils;
import org.openkilda.wfm.topology.isllatency.model.CacheEndpoint;
import org.openkilda.wfm.topology.isllatency.model.StreamType;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CacheBolt extends AbstractBolt {
    private final PersistenceManager persistenceManager;
    private IslRepository islRepository;

    private Map<CacheEndpoint, CacheEndpoint> cache = new HashMap<>();

    public CacheBolt(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    private void initCache() {
        cache.clear();
        try {
            islRepository.findAllActive()
                    .forEach(isl -> {
                        CacheEndpoint source = new CacheEndpoint(isl.getSrcSwitch().getSwitchId(), isl.getSrcPort());
                        CacheEndpoint destination = new CacheEndpoint(
                                isl.getDestSwitch().getSwitchId(), isl.getDestPort());

                        cache.put(source, destination);
                        cache.put(destination, source);
                    });
            log.debug("Isl latency cache: {}", cache);
            log.info("Isl latency cache: Initialized");
        } catch (Exception ex) {
            log.error("Error during isl latency cache initialization", ex);
        }
    }

    @Override
    public void init() {
        islRepository = persistenceManager.getRepositoryFactory().createIslRepository();
        initCache();
    }

    @Override
    protected void handleInput(Tuple tuple) throws PipelineException {

        if (ISL_STATUS_SPOUT_ID.equals(tuple.getSourceComponent())) {
            handleUpdateCache(tuple);
        } else if (ROUTER_BOLT_ID.equals(tuple.getSourceComponent())) {
            handleGetDataFromCache(tuple);
        } else {
            unhandledInput(tuple);
        }
    }

    private void handleGetDataFromCache(Tuple tuple) throws PipelineException {
        IslRoundTripLatency data = pullValue(tuple, LATENCY_DATA_FIELD, IslRoundTripLatency.class);
        long timestamp = tuple.getLongByField(TIMESTAMP_FIELD);

        CacheEndpoint source = new CacheEndpoint(data.getSrcSwitchId(), data.getSrcPortNo());

        CacheEndpoint destination;
        if (cache.containsKey(source)) {
            destination = cache.get(source);
        } else {
            destination = updateCache(data, source);
        }

        if (destination != null) {
            emitCachedData(tuple, data, destination, timestamp);
        }
    }

    private void emitCachedData(Tuple tuple, IslRoundTripLatency data, CacheEndpoint cachedData, long timestamp) {
        String islKey = Utils.createIslSwitchKey(data.getSrcSwitchId().toString(), cachedData.getSwitchId().toString());
        Values values = new Values(islKey, data, cachedData, timestamp, getCommandContext());
        getOutput().emit(StreamType.STATS.toString(), tuple, values);
        getOutput().emit(StreamType.LATENCY.toString(), tuple, values);
    }

    private CacheEndpoint updateCache(IslRoundTripLatency data, CacheEndpoint source) {
        List<Isl> activeIsls = islRepository.findBySrcEndpoint(data.getSrcSwitchId(), data.getSrcPortNo())
                .stream()
                .filter(isl -> isl.getStatus() == IslStatus.ACTIVE)
                .collect(Collectors.toList());

        if (activeIsls.isEmpty()) {
            log.info("There is no active ISLs with src endpoint {}_{}.", data.getSrcSwitchId(), data.getSrcPortNo());
            return null;
        }
        if (activeIsls.size() > 1) {
            List<String> destinationEndpoints = activeIsls.stream()
                    .map(isl -> String.format("%s_%d", isl.getDestSwitch(), isl.getDestPort()))
                    .collect(Collectors.toList());

            log.warn("There is more than one active ISLs with source endpoint {}_{}. Destination endpoints: {}",
                    data.getSrcSwitchId(), data.getSrcPortNo(), destinationEndpoints);
            return null;
        }

        Isl isl = activeIsls.get(0);
        CacheEndpoint destination = new CacheEndpoint(isl.getDestSwitch().getSwitchId(), isl.getDestPort());

        cache.put(source, destination);
        cache.put(destination, source);

        return destination;
    }

    private void handleUpdateCache(Tuple tuple) {
        IslMoveNotification data;
        try {
            data = pullValue(tuple, LATENCY_DATA_FIELD, IslMoveNotification.class);
        } catch (PipelineException e) {
            unhandledInput(tuple);
            return;
        }

        CacheEndpoint source = new CacheEndpoint(data.getSrcSwitchId(), data.getSrcPortNo());
        CacheEndpoint destination = new CacheEndpoint(data.getDstSwitchId(), data.getDstPortNo());

        cache.remove(source);
        cache.remove(destination);

        log.info("Remove ISL {}_{} ===> {}_{} from isl latency cache", data.getSrcSwitchId(), data.getSrcPortNo(),
                data.getDstSwitchId(), data.getDstPortNo());
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        Fields fields = new Fields(
                ISL_KEY_FIELD, LATENCY_DATA_FIELD, CACHE_DATA_FIELD, TIMESTAMP_FIELD, FIELD_ID_CONTEXT);
        declarer.declareStream(StreamType.STATS.toString(), fields);
        declarer.declareStream(StreamType.LATENCY.toString(), fields);
    }
}
