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

package org.openkilda.northbound.utils.flowhistory;

import static java.lang.String.format;

import org.openkilda.northbound.dto.v2.flows.FlowHistoryStatusesResponse;
import org.openkilda.northbound.service.FlowHistoryAware;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * This helper holds common functionality of preparing input and invokes proper history service methods.
 */
public final class FlowHistoryHelper {

    private FlowHistoryHelper() {
    }

    /**
     * Prepares default values if not provided by a user and retrieves flow history events from the service.
     * @param flowStatusHistoryAwareService a service that provides history entries
     * @param flowId retrieves statuses for this flow ID (mandatory)
     * @param constraints constraints for limiting the output. Includes: time from, time to, max entries.
     * @param <T> a type of entity for which the history were saved
     * @return a response containing flow history events with a CONTENT_RANGE header if needed.
     */
    public static <T> CompletableFuture<ResponseEntity<List<T>>> getFlowHistoryEvents(
            FlowHistoryAware<T> flowStatusHistoryAwareService,
            String flowId,
            FlowHistoryRangeConstraints constraints
    ) {
        return flowStatusHistoryAwareService.getFlowHistory(flowId, constraints)
                .thenApply(historyEventsListToResponseEntity(constraints));
    }

    /**
     * Prepares default values if not provided by a user and retrieves flow statuses from the service.
     * @param flowStatusHistoryAwareService a service that provides history entries
     * @param flowId retrieves statuses for this flow ID (mandatory)
     * @param constraints constraints for limiting the output. Includes: time from, time to, max entries.
     * @param <T> a type of entity for which the history were saved
     * @return a response containing flow statuses with a CONTENT_RANGE header if needed.
     */
    public static <T> CompletableFuture<ResponseEntity<FlowHistoryStatusesResponse>> getFlowStatuses(
            FlowHistoryAware<T> flowStatusHistoryAwareService,
            String flowId,
            FlowHistoryRangeConstraints constraints
    ) {
        return flowStatusHistoryAwareService.getFlowStatuses(flowId, constraints)
                .thenApply(statusesResponseToResponseEntity(constraints));
    }

    private static Function<FlowHistoryStatusesResponse, ResponseEntity<FlowHistoryStatusesResponse>>
                statusesResponseToResponseEntity(FlowHistoryRangeConstraints constraints) {
        return statusesResponse -> {
            HttpHeaders headers = getHeadersWithContentRange(constraints,
                    statusesResponse.getHistoryStatuses().size());

            return new ResponseEntity<>(statusesResponse, headers, HttpStatus.OK);
        };
    }

    private static <T> Function<List<T>, ResponseEntity<List<T>>>
                historyEventsListToResponseEntity(FlowHistoryRangeConstraints constraints) {
        return events -> {
            HttpHeaders headers = getHeadersWithContentRange(constraints,
                    events.size());

            return new ResponseEntity<>(events, headers, HttpStatus.OK);
        };
    }

    /**
     * This method adds a CONTENT_RANGE header when the constraints might limit the result set.
     * @param constraints conditions for filtering flow history result set
     * @param statusesListSize actual size of status entries
     * @return required HttpHeaders depending on the input
     */
    private static HttpHeaders getHeadersWithContentRange(FlowHistoryRangeConstraints constraints,
                                                   int statusesListSize) {
        HttpHeaders headers = new HttpHeaders();

        if (constraints.isContentRangeRequired(statusesListSize)) {
            headers.add(HttpHeaders.CONTENT_RANGE, format("items 0-%d/*",
                    statusesListSize - 1));
        }
        return headers;
    }
}
