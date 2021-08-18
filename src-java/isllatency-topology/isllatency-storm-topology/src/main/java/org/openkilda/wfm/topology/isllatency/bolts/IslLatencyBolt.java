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
import static org.openkilda.wfm.topology.isllatency.IslLatencyTopology.LATENCY_DATA_FIELD;

import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.event.IslOneWayLatency;
import org.openkilda.messaging.info.event.IslRoundTripLatency;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.tx.TransactionManager;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.share.model.Endpoint;
import org.openkilda.wfm.topology.isllatency.service.IslLatencyService;

import lombok.extern.slf4j.Slf4j;
import org.apache.storm.tuple.Tuple;

@Slf4j
public class IslLatencyBolt extends AbstractBolt {
    private final long latencyUpdateInterval; // emit data in DB interval
    private final long latencyUpdateTimeRange; // average latency will be calculated in this time range
    private transient IslLatencyService islLatencyService;

    public IslLatencyBolt(PersistenceManager persistenceManager, long latencyUpdateInterval,
                          long latencyUpdateTimeRange) {
        super(persistenceManager);
        this.latencyUpdateInterval = latencyUpdateInterval;
        this.latencyUpdateTimeRange = latencyUpdateTimeRange;
    }

    @Override
    protected void init() {
        TransactionManager transactionManager = persistenceManager.getTransactionManager();
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        islLatencyService = new IslLatencyService(transactionManager, repositoryFactory,
                latencyUpdateInterval, latencyUpdateTimeRange);
    }

    @Override
    protected void handleInput(Tuple input) throws PipelineException {
        InfoData data = pullValue(input, LATENCY_DATA_FIELD, InfoData.class);
        long timestamp = getCommandContext().getCreateTime();

        if (data instanceof IslRoundTripLatency) {
            Endpoint destination = pullValue(input, CACHE_DATA_FIELD, Endpoint.class);
            islLatencyService.handleRoundTripIslLatency((IslRoundTripLatency) data, destination, timestamp);
        } else if (data instanceof IslOneWayLatency) {
            islLatencyService.handleOneWayIslLatency((IslOneWayLatency) data, timestamp);
        } else {
            unhandledInput(input);
        }
    }
}
