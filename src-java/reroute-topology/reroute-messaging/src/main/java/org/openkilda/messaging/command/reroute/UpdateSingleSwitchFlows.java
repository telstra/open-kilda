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

package org.openkilda.messaging.command.reroute;

import org.openkilda.messaging.command.CommandData;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchStatus;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = false)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
public class UpdateSingleSwitchFlows extends CommandData {

    @JsonProperty
    private SwitchId switchId;

    @JsonProperty
    private SwitchStatus status;

    @JsonCreator
    public UpdateSingleSwitchFlows(@NonNull @JsonProperty("switch_id")SwitchId switchId,
                                   @NonNull @JsonProperty("status") SwitchStatus status) {
        this.switchId = switchId;
        this.status = status;
    }
}
