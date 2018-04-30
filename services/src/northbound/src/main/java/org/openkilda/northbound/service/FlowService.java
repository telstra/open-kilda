/* Copyright 2017 Telstra Open Source
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

package org.openkilda.northbound.service;

import org.openkilda.messaging.command.flow.SynchronizeCacheAction;
import org.openkilda.messaging.info.flow.FlowInfoData;
import org.openkilda.messaging.payload.flow.FlowCacheSyncResults;
import org.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.openkilda.messaging.payload.flow.FlowPathPayload;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.messaging.payload.flow.FlowReroutePayload;
import org.openkilda.northbound.dto.BatchResults;
import org.openkilda.northbound.dto.flows.FlowValidationDto;
import org.openkilda.northbound.dto.flows.VerificationInput;
import org.openkilda.northbound.dto.flows.VerificationOutput;

import java.util.List;

/**
 * FlowService is for operations on flows, primarily against the Flow Topology.
 */
public interface FlowService extends BasicService {
    /**
     * Creates flow.
     *
     * @param flow          flow
     * @return created flow
     */
    FlowPayload createFlow(final FlowPayload flow);

    /**
     * Deletes flow.
     *
     * @param id            flow id
     * @return deleted flow
     */
    FlowPayload deleteFlow(final String id);

    /**
     * Updates flow.
     *
     * @param flow          flow
     * @return updated flow
     */
    FlowPayload updateFlow(final FlowPayload flow);

    /**
     * Gets flow by id.
     *
     * @param id            flow id
     * @return flow
     */
    FlowPayload getFlow(final String id);

    /**
     * Gets all the flows.
     *
     * @return the list of all flows with specified status
     */
    List<FlowPayload> getFlows();

    /**
     * Deletes all flows. Primarily this is a combination of getFlows and deleteFlow.
     * This should be called with care ..
     *
     * @return the list of all deleted flows
     */
    List<FlowPayload> deleteFlows();

    /**
     * Gets flow status by id.
     *
     * @param id            flow id
     * @return flow status
     */
    FlowIdStatusPayload statusFlow(final String id);

    /**
     * Gets flow path by id.
     *
     * @param id            flow id
     * @return Flow path
     */
    FlowPathPayload pathFlow(final String id);

    /**
     * Use this to push flows that may not be in the database / caches but they should be
     *
     * @param externalFlows   the list of flows to push.
     * @param propagate if true, the path/rules will be propagated to the switch
     * @param verify if true, we'll wait up to poll seconds to confirm if rules have been applied
     *
     * @return
     */
    BatchResults pushFlows(final List<FlowInfoData> externalFlows, Boolean propagate, Boolean verify);

    /**
     * Use this to unpush flows .. ie undo a push
     *
     * @param externalFlows   the list of flows to unpush.
     * @param propagate if true, the path/rules will be propagated to the switch
     * @param verify if true, we'll wait up to poll seconds to confirm if rules have been applied
     * @return
     */
    BatchResults unpushFlows(final List<FlowInfoData> externalFlows, Boolean propagate, Boolean verify);

    /**
     * Performs rerouting of specific flow.
     *
     * @param flowId id of flow to be rerouted.
     * @return updated flow path information with the result whether or not path was changed.
     */
    FlowReroutePayload rerouteFlow(final String flowId);

    /**
     * Performs synchronization (reinstalling) of specific flow.
     *
     * @param flowId id of flow to be synchronized.
     * @return updated flow.
     */
    FlowReroutePayload syncFlow(final String flowId);

    /**
     * Performs validation of specific flow - ie comparing what is in the database with what is
     * on the network.
     *
     * @param flowId id of the flow
     * @return the results of the comparison, or null if the flow isn't found.
     * @throws java.nio.file.InvalidPathException if the flow doesn't return a path and it should.
     */
    List<FlowValidationDto> validateFlow(final String flowId);

    VerificationOutput verifyFlow(String flowId, VerificationInput payload);

    /**
     * Sync the FlowCache in the flow topology (in case it is out of sync.
     *
     * @param syncCacheAction describes how to synchronize the cache.
     * @return details on performed updates.
     */
    FlowCacheSyncResults syncFlowCache(SynchronizeCacheAction syncCacheAction);
}
