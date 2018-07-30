/* Copyright 2018 Telstra Open Source
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

package org.openkilda.messaging.info.discovery;

import static java.util.Objects.requireNonNull;

import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Value;

/**
 * Defines the {@link InfoMessage} payload representing a switch for network sync process.
 */
@Value
@Builder
@JsonNaming(SnakeCaseStrategy.class)
public class NetworkDumpSwitchData extends InfoData {
    private static final long serialVersionUID = 1L;

    private String switchId;

    @JsonCreator
    public NetworkDumpSwitchData(@JsonProperty("switch_id") String switchId) {
        this.switchId = requireNonNull(switchId);
    }
}
