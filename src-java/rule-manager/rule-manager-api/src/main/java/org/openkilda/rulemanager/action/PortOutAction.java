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

package org.openkilda.rulemanager.action;

import org.openkilda.rulemanager.ProtoConstants.PortNumber;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Value;

@Value
@JsonSerialize
@JsonNaming(SnakeCaseStrategy.class)
@JsonIgnoreProperties(value = { "type" })
public class PortOutAction implements Action {

    PortNumber portNumber;

    @JsonCreator
    public PortOutAction(@JsonProperty("port_number") PortNumber portNumber) {
        this.portNumber = portNumber;
    }

    @Override
    public ActionType getType() {
        return ActionType.PORT_OUT;
    }
}
