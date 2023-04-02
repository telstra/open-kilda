/* Copyright 2023 Telstra Open Source
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

import org.openkilda.northbound.dto.v2.haflows.HaFlow;
import org.openkilda.northbound.dto.v2.haflows.HaFlowCreatePayload;
import org.openkilda.northbound.dto.v2.haflows.HaFlowDump;
import org.openkilda.northbound.dto.v2.haflows.HaFlowPatchPayload;
import org.openkilda.northbound.dto.v2.haflows.HaFlowPaths;
import org.openkilda.northbound.dto.v2.haflows.HaFlowPingPayload;
import org.openkilda.northbound.dto.v2.haflows.HaFlowPingResult;
import org.openkilda.northbound.dto.v2.haflows.HaFlowRerouteResult;
import org.openkilda.northbound.dto.v2.haflows.HaFlowSyncResult;
import org.openkilda.northbound.dto.v2.haflows.HaFlowUpdatePayload;
import org.openkilda.northbound.dto.v2.haflows.HaFlowValidationResult;

import java.util.concurrent.CompletableFuture;

/**
 * HaFlowService is for processing of HA-flow operations.
 */
public interface HaFlowService {
    CompletableFuture<HaFlow> createHaFlow(HaFlowCreatePayload createPayload);

    CompletableFuture<HaFlowDump> dumpHaFlows();

    CompletableFuture<HaFlow> getHaFlow(String haFlowId);

    CompletableFuture<HaFlowPaths> getHaFlowPaths(String haFlowId);

    CompletableFuture<HaFlow> updateHaFlow(String haFlowId, HaFlowUpdatePayload updatePayload);

    CompletableFuture<HaFlow> patchHaFlow(String haFlowId, HaFlowPatchPayload patchPayload);

    CompletableFuture<HaFlow> deleteHaFlow(String haFlowId);

    CompletableFuture<HaFlowRerouteResult> rerouteHaFlow(String haFlowId);

    CompletableFuture<HaFlowValidationResult> validateHaFlow(String haFlowId);

    CompletableFuture<HaFlowSyncResult> synchronizeHaFlow(String haFlowId);

    CompletableFuture<HaFlowPingResult> pingHaFlow(String haFlowId, HaFlowPingPayload payload);

    CompletableFuture<HaFlow> swapHaFlowPaths(String haFlowId);
}
