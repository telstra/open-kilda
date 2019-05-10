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

package org.openkilda.messaging.command;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.io.Serializable;
import java.util.List;

@Value
public class CommandGroup implements Serializable {
    @JsonProperty("commands")
    private List<? extends CommandData> commands;
    @JsonProperty("reaction_on_error")
    private FailureReaction reactionOnError;

    public CommandGroup(@JsonProperty("commands") List<? extends CommandData> commands,
                        @JsonProperty("reaction_on_error") FailureReaction reactionOnError) {
        this.commands = commands;
        this.reactionOnError = reactionOnError;
    }

    public enum FailureReaction {
        IGNORE,
        ABORT_BATCH
    }
}
