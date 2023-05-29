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

import org.openkilda.northbound.dto.v2.flows.FlowHistoryStatusesResponse;
import org.openkilda.northbound.utils.flowhistory.FlowHistoryRangeConstraints;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * A service implementing this interface is able to retrieve flow history events from history.
 * This interface is flow-agnostic.
 */
public interface FlowHistoryAware<T> {

    /**
     * Retrieves flow history events with flow dumps from the persistence layer.
     * @param flowId get flow statuses for this flow ID
     * @param constraints a set of constraints to filter flow history result set
     * @return a response containing history entries
     */
    CompletableFuture<List<T>> getFlowHistory(String flowId,
                                                             FlowHistoryRangeConstraints constraints);

    /**
     * Retrieves flow statuses from the persistence layer.
     * @param flowId get flow statuses for this flow ID
     * @param constraints a set of constraints to filter flow history result set
     * @return a response containing history entries
     */
    CompletableFuture<FlowHistoryStatusesResponse> getFlowStatuses(String flowId,
                                                                   FlowHistoryRangeConstraints constraints);
}
