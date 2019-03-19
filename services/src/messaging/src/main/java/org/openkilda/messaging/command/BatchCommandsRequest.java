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

import java.util.List;

/**
 * Defines the payload of a message representing a batch of grouped commands. The batch must be
 * either completely processed group by group or canceled as a whole.
 */
@Value
public class BatchCommandsRequest extends CommandData {
    @JsonProperty("groups")
    private List<CommandGroup> groups;

    @JsonProperty("on_success")
    private List<? extends CommandData> onSuccessCommands;

    @JsonProperty("on_failure")
    private List<? extends CommandData> onFailureCommands;

    public BatchCommandsRequest(@JsonProperty("groups") List<CommandGroup> groups,
                                @JsonProperty("on_success") List<? extends CommandData> onSuccessCommands,
                                @JsonProperty("on_failure") List<? extends CommandData> onFailureCommands) {
        this.groups = groups;
        this.onSuccessCommands = onSuccessCommands;
        this.onFailureCommands = onFailureCommands;
    }
}
