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
import org.openkilda.northbound.dto.FlowValidationDto;

import java.util.List;

/**
 * FlowService is for operations on flows, primarily against the Flow Topology.
 */
public interface FlowService extends BasicService {
    /**
     * Creates flow.
     *
     * @param flow          flow
     * @param correlationId request correlation Id
     * @return created flow
     */
    FlowPayload createFlow(final FlowPayload flow, final String correlationId);

    /**
     * Deletes flow.
     *
     * @param id            flow id
     * @param correlationId request correlation Id
     * @return deleted flow
     */
    FlowPayload deleteFlow(final String id, final String correlationId);

    /**
     * Updates flow.
     *
     * @param flow          flow
     * @param correlationId request correlation Id
     * @return updated flow
     */
    FlowPayload updateFlow(final FlowPayload flow, final String correlationId);

    /**
     * Gets flow by id.
     *
     * @param id            flow id
     * @param correlationId request correlation Id
     * @return flow
     */
    FlowPayload getFlow(final String id, final String correlationId);

    /**
     * Gets all the flows.
     *
     * @param correlationId request correlation id
     * @return the list of all flows with specified status
     */
    List<FlowPayload> getFlows(final String correlationId);

    /**
     * Deletes all flows. Primarily this is a combination of getFlows and deleteFlow.
     * This should be called with care ..
     *
     * @param correlationId request correlation Id
     * @return the list of all deleted flows
     */
    List<FlowPayload> deleteFlows(final String correlationId);

    /**
     * Gets flow status by id.
     *
     * @param id            flow id
     * @param correlationId request correlation Id
     * @return flow status
     */
    FlowIdStatusPayload statusFlow(final String id, final String correlationId);

    /**
     * Gets flow path by id.
     *
     * @param id            flow id
     * @param correlationId request correlation Id
     * @return Flow path
     */
    FlowPathPayload pathFlow(final String id, final String correlationId);

    /**
     * Use this to push flows that may not be in the database / caches but they should be
     *
     * @param externalFlows   the list of flows to push.
     * @param correlationId request correlation Id
     * @param propagate if true, the path/rules will be propagated to the switch
     * @param verify if true, we'll wait up to poll seconds to confirm if rules have been applied
     *
     * @return
     */
    BatchResults pushFlows(final List<FlowInfoData> externalFlows, final String correlationId,
                           Boolean propagate, Boolean verify
    );

    /**
     * Use this to unpush flows .. ie undo a push
     *
     * @param externalFlows   the list of flows to unpush.
     * @param correlationId request correlation Id
     * @param propagate if true, the path/rules will be propagated to the switch
     * @param verify if true, we'll wait up to poll seconds to confirm if rules have been applied
     * @return
     */
    BatchResults unpushFlows(final List<FlowInfoData> externalFlows, final String correlationId,
                             Boolean propagate, Boolean verify
    );

    /**
     * Performs rerouting of specific flow.
     *
     * @param flowId id of flow to be rerouted.
     * @param correlationId request correlation Id
     * @return updated flow path information with the result whether or not path was changed.
     */
    FlowReroutePayload rerouteFlow(final String flowId, final String correlationId);


    /**
     * Performs validation of specific flow - ie comparing what is in the database with what is
     * on the network.
     *
     * @param flowId id of the flow
     * @param correlationId request correlation Id
     * @return the results of the comparison, or null if the flow isn't found.
     * @throws java.nio.file.InvalidPathException if the flow doesn't return a path and it should.
     */
    List<FlowValidationDto> validateFlow(final String flowId, final String correlationId);

    /**
     * Sync the FlowCache in the flow topology (in case it is out of sync.
     *
     * @param syncCacheAction describes how to synchronize the cache.
     * @param correlationId request correlation Id
     * @return details on performed updates.
     */
    FlowCacheSyncResults syncFlowCache(SynchronizeCacheAction syncCacheAction, String correlationId);
}
