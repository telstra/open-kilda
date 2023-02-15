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

package org.openkilda.northbound.service.impl;

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
import org.openkilda.northbound.service.HaFlowService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Handles the HA-flow operations.
 */
@Slf4j
@Service
public class HaFlowServiceImpl implements HaFlowService {
    @Override
    public CompletableFuture<HaFlow> createHaFlow(HaFlowCreatePayload createPayload) {
        return null;
    }

    @Override
    public CompletableFuture<HaFlowDump> dumpHaFlows() {
        return null;
    }

    @Override
    public CompletableFuture<HaFlow> getHaFlow(String haFlowId) {
        return null;
    }

    @Override
    public CompletableFuture<HaFlowPaths> getHaFlowPaths(String haFlowId) {
        return null;
    }

    @Override
    public CompletableFuture<HaFlow> updateHaFlow(String haFlowId, HaFlowUpdatePayload updatePayload) {
        return null;
    }

    @Override
    public CompletableFuture<HaFlow> patchHaFlow(String haFlowId, HaFlowPatchPayload patchPayload) {
        return null;
    }

    @Override
    public CompletableFuture<HaFlow> deleteHaFlow(String haFlowId) {
        return null;
    }

    @Override
    public CompletableFuture<HaFlowRerouteResult> rerouteHaFlow(String haFlowId) {
        return null;
    }

    @Override
    public CompletableFuture<HaFlowValidationResult> validateHaFlow(String haFlowId) {
        return null;
    }

    @Override
    public CompletableFuture<HaFlowSyncResult> synchronizeHaFlow(String haFlowId) {
        return null;
    }

    @Override
    public CompletableFuture<HaFlowPingResult> pingHaFlow(String haFlowId, HaFlowPingPayload payload) {
        return null;
    }

    @Override
    public CompletableFuture<HaFlow> swapHaFlowPaths(String haFlowId) {
        return null;
    }
}
