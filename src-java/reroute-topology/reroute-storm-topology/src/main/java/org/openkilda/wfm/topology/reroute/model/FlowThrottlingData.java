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

package org.openkilda.wfm.topology.reroute.model;

import org.openkilda.model.PathId;

import com.google.common.annotations.VisibleForTesting;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.time.Instant;
import java.util.Collections;
import java.util.Set;

@Data
@AllArgsConstructor
public class FlowThrottlingData implements Serializable {
    private String correlationId;
    private Integer priority;
    private Instant timeCreate;
    private Set<PathId> pathIdSet;

    @VisibleForTesting
    public FlowThrottlingData(String correlationId, Integer priority) {
        this.correlationId = correlationId;
        this.priority = priority;
        pathIdSet = Collections.emptySet();
    }
}
