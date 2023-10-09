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

package org.openkilda.wfm.share.history.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Value;

import java.io.Serializable;
import java.time.Instant;

/**
 * This class holds the information about an event performed on HA-flow.
 * Fields action and details are loosely defined and could mean different things depending on the context. It is best
 * to have an action as a short phrase.
 */
@Value
@Builder
@EqualsAndHashCode(callSuper = false)
public class HaFlowEventData implements Serializable {
    String haFlowId;
    Initiator initiator;
    Event event;
    String details;
    Instant time;
    String taskId;
    String action;

    @Getter
    public enum Initiator {
        NB,
        AUTO
    }

    @AllArgsConstructor
    @Getter
    public enum Event {
        CREATE("HA-Flow create"),
        UPDATE("HA-Flow update"),
        PARTIAL_UPDATE("HA-Flow partial update"),
        REROUTE("HA-Flow reroute"),
        DELETE("HA-Flow delete"),
        PATH_SWAP("HA-flow path swap");

        private final String description;
    }
}
