/* Copyright 2017 Telstra Open Source
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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public enum FlowOperation {
    PUSH("Push"),       // used to pre-populate flows
    PUSH_PROPAGATE("Push_Propagate"),       // used to pre-populate flows, and push to switch
    UNPUSH("Unpush"),   // used to un-pre-populate flows
    UNPUSH_PROPAGATE("Unpush_Propagate");   // used to un-pre-populate flows, and push to switch

    @JsonProperty("operation")
    private final String operation;

    @JsonCreator
    FlowOperation(@JsonProperty("operation") String operation) {
        this.operation = operation;
    }

    public String getOperation() {
        return this.operation;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return operation;
    }
}

