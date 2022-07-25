/* Copyright 2022 Telstra Open Source
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

import static org.openkilda.wfm.topology.flowmonitoring.bolt.FlowCacheBolt.FLOW_DIRECTION_FIELD;
import static org.openkilda.wfm.topology.flowmonitoring.bolt.FlowCacheBolt.FLOW_ID_FIELD;
import static org.openkilda.wfm.topology.flowmonitoring.bolt.FlowCacheBolt.LATENCY_FIELD;

import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.topology.flowmonitoring.service.FlowStatsService;

import org.apache.storm.tuple.Tuple;

public class FlowStatsBolt extends AbstractBolt {

    private transient FlowStatsService service;

    public FlowStatsBolt(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    protected void init() {
        service = new FlowStatsService(persistenceManager);
    }

    @Override
    protected void handleInput(Tuple input) throws PipelineException {
        String flowId = pullValue(input, FLOW_ID_FIELD, String.class);
        String direction = pullValue(input, FLOW_DIRECTION_FIELD, String.class);
        Long latency = pullValue(input, LATENCY_FIELD, Long.class);

        service.persistFlowStats(flowId, direction, latency);
    }
}
