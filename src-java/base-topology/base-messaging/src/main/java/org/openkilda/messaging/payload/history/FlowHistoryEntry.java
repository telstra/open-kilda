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

package org.openkilda.messaging.payload.history;

import org.openkilda.messaging.info.InfoData;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

import java.util.List;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@Value
@EqualsAndHashCode(callSuper = false)
public class FlowHistoryEntry extends InfoData {
    @JsonProperty("flow_id")
    private String flowId;

    @JsonProperty("timestamp")
    private long timestamp;

    @JsonProperty("actor")
    private String actor;

    @JsonProperty("action")
    private String action;

    @JsonProperty("task_id")
    private String taskId;

    @JsonProperty("details")
    private String details;

    @JsonProperty("payload")
    private List<FlowHistoryPayload> payload;

    @JsonProperty("dumps")
    private List<FlowDumpPayload> dumps;

    @Builder
    public FlowHistoryEntry(
            @JsonProperty("flow_id") String flowId,
            @JsonProperty("timestamp") long timestamp,
            @JsonProperty("actor") String actor,
            @JsonProperty("action") String action,
            @JsonProperty("task_id") String taskId,
            @JsonProperty("details") String details,
            @JsonProperty("payload") List<FlowHistoryPayload> payload,
            @JsonProperty("dumps") List<FlowDumpPayload> dumps) {
        this.flowId = flowId;
        this.timestamp = timestamp;
        this.actor = actor;
        this.action = action;
        this.taskId = taskId;
        this.details = details;
        this.payload = payload;
        this.dumps = dumps;
    }
}
