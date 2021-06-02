/* Copyright 2020 Telstra Open Source
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

package org.openkilda.wfm.share.history.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Value;

import java.io.Serializable;
import java.time.Instant;

@Value
@Builder
public class FlowEventData implements Serializable {
    private String flowId;
    private Initiator initiator;
    private Event event;
    private String details;
    private Instant time;

    @Getter
    public enum Initiator {
        NB,
        AUTO
    }

    @AllArgsConstructor
    @Getter
    public enum Event {
        CREATE("Flow creating"),
        UPDATE("Flow updating"),
        REROUTE("Flow rerouting"),
        DELETE("Flow deleting"),
        PATH_SWAP("Flow paths swap"),
        SWAP_ENDPOINTS("Flows swap endpoints"),
        FLOW_LOOP_CREATE("Flow loop creating"),
        FLOW_LOOP_DELETE("Flow loop deleting"),
        FLOW_MIRROR_POINT_CREATE("Flow mirror point creating"),
        FLOW_MIRROR_POINT_DELETE("Flow mirror point deleting");

        private String description;
    }
}
