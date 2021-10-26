/* Copyright 2021 Telstra Open Source
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

import org.openkilda.northbound.dto.v2.yflows.SubFlowsDump;
import org.openkilda.northbound.dto.v2.yflows.YFlow;
import org.openkilda.northbound.dto.v2.yflows.YFlowCreatePayload;
import org.openkilda.northbound.dto.v2.yflows.YFlowDump;
import org.openkilda.northbound.dto.v2.yflows.YFlowPatchPayload;
import org.openkilda.northbound.dto.v2.yflows.YFlowPaths;
import org.openkilda.northbound.dto.v2.yflows.YFlowRerouteResult;
import org.openkilda.northbound.dto.v2.yflows.YFlowUpdatePayload;

import java.util.concurrent.CompletableFuture;

/**
 * YFlowService is for processing of Y-flow operations.
 */
public interface YFlowService {
    CompletableFuture<YFlow> createYFlow(YFlowCreatePayload createPayload);

    CompletableFuture<YFlowDump> dumpYFlows();

    CompletableFuture<YFlow> getYFlow(String flowId);

    CompletableFuture<YFlowPaths> getYFlowPaths(String flowId);

    CompletableFuture<YFlow> updateYFlow(String flowId, YFlowUpdatePayload updatePayload);

    CompletableFuture<YFlow> patchYFlow(String flowId, YFlowPatchPayload patchPayload);

    CompletableFuture<YFlow> deleteYFlow(String flowId);

    CompletableFuture<SubFlowsDump> getSubFlows(String flowId);

    CompletableFuture<YFlowRerouteResult> rerouteYFlow(String flowId);
}
