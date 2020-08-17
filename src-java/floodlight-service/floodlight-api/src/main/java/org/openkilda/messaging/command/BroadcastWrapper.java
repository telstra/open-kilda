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

package org.openkilda.messaging.command;

import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Value;

import java.util.Set;

/**
 * Wrapper added to requests transferred into the speaker. This wrapper required for broadcast requests i.e. requests
 * that interact with multiple(all) switches known to the system. Because a switch can be connected to multiple speakers
 * we need a way to inform the speaker about the scope (set of switches) targeted by this broadcast request. So each
 * switch will be processed only once(only one speaker will receive it into the scope) per broadcast request.
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class BroadcastWrapper extends CommandData {
    @JsonProperty("scope")
    private Set<SwitchId> scope;

    @JsonProperty("payload")
    private CommandData payload;

    @JsonCreator
    public BroadcastWrapper(
            @JsonProperty("scope") Set<SwitchId> scope,
            @JsonProperty("payload") CommandData payload) {
        this.scope = scope;
        this.payload = payload;
    }
}
