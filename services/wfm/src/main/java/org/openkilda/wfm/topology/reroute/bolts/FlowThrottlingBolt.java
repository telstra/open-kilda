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

package org.openkilda.wfm.topology.reroute.bolts;

import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.flow.FlowRerouteRequest;
import org.openkilda.model.FeatureToggles;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FeatureTogglesRepository;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.topology.reroute.RerouteTopology;
import org.openkilda.wfm.topology.reroute.model.FlowThrottlingData;
import org.openkilda.wfm.topology.reroute.service.ReroutesThrottling;
import org.openkilda.wfm.topology.utils.AbstractTickStatefulBolt;

import org.apache.storm.state.InMemoryKeyValueState;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.Map;

public class FlowThrottlingBolt extends AbstractTickStatefulBolt<InMemoryKeyValueState<String, ReroutesThrottling>> {

    private static final String REROUTES_THROTTLING = "reroutes-throttling";

    public static final String STREAM_FLOW_ID = "flow";
    public static final String STREAM_FLOWHS_ID = "flowhs";

    private final PersistenceManager persistenceManager;

    private final long minDelay;
    private final long maxDelay;
    private final int defaultFlowPriority;

    private transient ReroutesThrottling reroutesThrottling;
    private transient FeatureTogglesRepository featureTogglesRepository;

    public FlowThrottlingBolt(PersistenceManager persistenceManager,
                              long minDelay, long maxDelay, int defaultFlowPriority) {
        this.persistenceManager = persistenceManager;

        this.minDelay = minDelay;
        this.maxDelay = maxDelay;
        this.defaultFlowPriority = defaultFlowPriority;
    }

    @Override
    protected void doTick(Tuple tuple) {
        for (Map.Entry<String, FlowThrottlingData> entry : reroutesThrottling.getReroutes()) {
            String flowId = entry.getKey();

            boolean flowsRerouteViaFlowHs = featureTogglesRepository.find()
                    .map(FeatureToggles::getFlowsRerouteViaFlowHs)
                    .orElse(FeatureToggles.DEFAULTS.getFlowsRerouteViaFlowHs());

            FlowThrottlingData throttlingData = entry.getValue();
            CommandContext forkedContext = new CommandContext(throttlingData.getCorrelationId()).fork(flowId);

            FlowRerouteRequest request = new FlowRerouteRequest(flowId, false, throttlingData.getPathIdSet());
            outputCollector.emit(flowsRerouteViaFlowHs ? STREAM_FLOWHS_ID : STREAM_FLOW_ID,
                    tuple, new Values(forkedContext.getCorrelationId(),
                            new CommandMessage(request, System.currentTimeMillis(),
                                    forkedContext.getCorrelationId())));
        }
        outputCollector.ack(tuple);
    }

    @Override
    protected void doWork(Tuple tuple) {
        FlowThrottlingData throttlingData =
                (FlowThrottlingData) tuple.getValueByField(RerouteBolt.THROTTLING_DATA_FIELD);
        reroutesThrottling.putRequest(tuple.getStringByField(RerouteBolt.FLOW_ID_FIELD), throttlingData);
        outputCollector.ack(tuple);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        outputFieldsDeclarer.declareStream(STREAM_FLOW_ID, RerouteTopology.KAFKA_FIELDS);
        outputFieldsDeclarer.declareStream(STREAM_FLOWHS_ID, RerouteTopology.KAFKA_FIELDS);
    }

    @Override
    public void initState(InMemoryKeyValueState<String, ReroutesThrottling> state) {
        reroutesThrottling = state.get(REROUTES_THROTTLING);
        if (reroutesThrottling == null) {
            reroutesThrottling = new ReroutesThrottling(minDelay, maxDelay, defaultFlowPriority);
            state.put(REROUTES_THROTTLING, reroutesThrottling);
        }

        featureTogglesRepository = persistenceManager.getRepositoryFactory().createFeatureTogglesRepository();
    }
}
