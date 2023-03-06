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

package org.openkilda.wfm.topology.flowhs.bolts;

import org.openkilda.messaging.command.flow.FlowSyncRequest;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.share.flow.resources.FlowResourcesConfig;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.topology.flowhs.FlowHsTopology.ComponentId;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathOperationConfig;
import org.openkilda.wfm.topology.flowhs.service.FlowSyncService;
import org.openkilda.wfm.topology.utils.KafkaRecordTranslator;

import lombok.NonNull;
import org.apache.storm.tuple.Tuple;

public class FlowSyncHubBolt extends SyncHubBoltBase<FlowSyncService> {
    public static final String BOLT_ID = ComponentId.FLOW_SYNC_HUB.name();

    public FlowSyncHubBolt(
            @NonNull SyncHubConfig config, @NonNull PersistenceManager persistenceManager,
            @NonNull FlowResourcesConfig flowResourcesConfig) {
        super(config, persistenceManager, flowResourcesConfig);
    }

    @Override
    protected void onRequest(Tuple input) throws PipelineException {
        FlowSyncRequest request = pullValue(input, KafkaRecordTranslator.FIELD_ID_PAYLOAD, FlowSyncRequest.class);
        carrierContext.apply(pullKey(input), key -> handleRequest(key, request));
    }

    private void handleRequest(String serviceKey, FlowSyncRequest request) {
        syncService.handleRequest(serviceKey, request, getCommandContext());
    }

    @Override
    protected FlowSyncService newSyncService() {
        final FlowResourcesManager resourcesManager = new FlowResourcesManager(persistenceManager, flowResourcesConfig);
        final FlowPathOperationConfig pathOperationConfig = new FlowPathOperationConfig(
                config.getSpeakerCommandRetriesLimit());
        return new FlowSyncService(this, persistenceManager, resourcesManager, pathOperationConfig);
    }
}
