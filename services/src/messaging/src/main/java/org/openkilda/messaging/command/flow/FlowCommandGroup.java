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

package org.openkilda.messaging.command.flow;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.io.Serializable;
import java.util.List;

@Value
public class FlowCommandGroup implements Serializable {
    @JsonProperty("flow_commands")
    private List<? extends BaseFlow> flowCommands;
    @JsonProperty("reaction_on_error")
    private FailureReaction reactionOnError;

    public FlowCommandGroup(@JsonProperty("flow_commands") List<? extends BaseFlow> flowCommands,
                            @JsonProperty("reaction_on_error") FailureReaction reactionOnError) {
        this.flowCommands = flowCommands;
        this.reactionOnError = reactionOnError;
    }

    public enum FailureReaction {
        IGNORE,
        ABORT_FLOW
    }
}
