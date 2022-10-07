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

import org.openkilda.floodlight.api.response.SpeakerResponse;
import org.openkilda.floodlight.api.response.rulemanager.SpeakerCommandResponse;
import org.openkilda.messaging.command.yflow.YFlowSyncRequest;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.rulemanager.RuleManagerConfig;
import org.openkilda.rulemanager.RuleManagerImpl;
import org.openkilda.wfm.share.flow.resources.FlowResourcesConfig;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.topology.flowhs.FlowHsTopology.ComponentId;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathOperationConfig;
import org.openkilda.wfm.topology.flowhs.service.YFlowSyncService;
import org.openkilda.wfm.topology.utils.KafkaRecordTranslator;

import lombok.NonNull;
import org.apache.storm.tuple.Tuple;

public class YFlowSyncHubBolt extends SyncHubBoltBase<YFlowSyncService> {
    public static final String BOLT_ID = ComponentId.YFLOW_SYNC_HUB.name();

    private final RuleManagerConfig ruleManagerConfig;

    public YFlowSyncHubBolt(
            @NonNull SyncHubConfig config, @NonNull PersistenceManager persistenceManager,
            @NonNull FlowResourcesConfig flowResourcesConfig, @NonNull RuleManagerConfig ruleManagerConfig) {
        super(config, persistenceManager, flowResourcesConfig);
        this.ruleManagerConfig = ruleManagerConfig;
    }

    @Override
    protected void onRequest(Tuple input) throws Exception {
        YFlowSyncRequest request = pullValue(input, KafkaRecordTranslator.FIELD_ID_PAYLOAD, YFlowSyncRequest.class);
        carrierContext.apply(pullKey(input), key -> handleRequest(key, request));
    }

    private void handleRequest(String serviceKey, YFlowSyncRequest request) {
        syncService.handleRequest(serviceKey, request, getCommandContext());
    }

    @Override
    protected void dispatchWorkerResponse(String workerKey, SpeakerResponse response) {
        boolean handled = false;
        if (response instanceof SpeakerCommandResponse) {
            handled = syncService.handleSpeakerResponse((SpeakerCommandResponse) response);
        }
        if (! handled) {
            super.dispatchWorkerResponse(workerKey, response);
        }
    }

    // -- storm API --

    @Override
    protected YFlowSyncService newSyncService() {
        final FlowResourcesManager resourcesManager = new FlowResourcesManager(persistenceManager, flowResourcesConfig);
        final FlowPathOperationConfig pathOperationConfig = new FlowPathOperationConfig(
                config.getSpeakerCommandRetriesLimit());
        RuleManager ruleManager = new RuleManagerImpl(ruleManagerConfig);
        return new YFlowSyncService(this, persistenceManager, resourcesManager, ruleManager, pathOperationConfig);
    }
}
