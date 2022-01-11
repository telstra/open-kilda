/* Copyright 2021 Telstra Open Source
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

package org.openkilda.rulemanager.group;

import org.openkilda.rulemanager.action.Action;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Builder;
import lombok.Value;

import java.util.Set;

@Value
@JsonSerialize
@Builder
@JsonNaming(SnakeCaseStrategy.class)
public class Bucket {

    WatchGroup watchGroup;
    WatchPort watchPort;
    Set<Action> writeActions;

    @JsonCreator
    public Bucket(@JsonProperty("watch_group") WatchGroup watchGroup,
                  @JsonProperty("watch_port") WatchPort watchPort,
                  @JsonProperty("write_actions") Set<Action> writeActions) {
        this.watchGroup = watchGroup;
        this.watchPort = watchPort;
        this.writeActions = writeActions;
    }
}
