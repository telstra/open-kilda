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

package org.openkilda.messaging.info.flow;

import org.openkilda.messaging.info.InfoData;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Value;

import java.util.List;

@Value
@EqualsAndHashCode(callSuper = false)
public class FlowEnabledApplicationsResponse extends InfoData {
    private static final long serialVersionUID = 1L;

    @JsonProperty("flow_id")
    protected String flowId;

    @JsonProperty("enabled_applications")
    private List<String> enabledApplications;

    @JsonCreator
    public FlowEnabledApplicationsResponse(@JsonProperty("flow_id") String flowId,
                                           @JsonProperty("enabled_applications") List<String> enabledApplications) {
        this.flowId = flowId;
        this.enabledApplications = enabledApplications;
    }
}
