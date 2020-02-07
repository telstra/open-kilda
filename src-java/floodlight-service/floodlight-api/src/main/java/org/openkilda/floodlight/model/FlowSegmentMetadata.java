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

package org.openkilda.floodlight.model;

import org.openkilda.model.Cookie;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.NonNull;
import lombok.Value;

import java.io.Serializable;

@Value
public class FlowSegmentMetadata implements Serializable {
    @JsonProperty("flowid")
    private final String flowId;

    @JsonProperty("cookie")
    private final Cookie cookie;

    @JsonProperty("multi_table")
    private final boolean multiTable;

    @JsonCreator
    public FlowSegmentMetadata(
            @JsonProperty("flowid") @NonNull String flowId,
            @JsonProperty("cookie") @NonNull Cookie cookie,
            @JsonProperty("multi_table") boolean multiTable) {
        this.flowId = flowId;
        this.cookie = cookie;
        this.multiTable = multiTable;
    }
}
