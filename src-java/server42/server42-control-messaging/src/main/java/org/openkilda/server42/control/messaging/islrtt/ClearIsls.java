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

package org.openkilda.server42.control.messaging.islrtt;

import org.openkilda.model.SwitchId;
import org.openkilda.server42.control.messaging.flowrtt.Headers;
import org.openkilda.server42.control.messaging.flowrtt.Message;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@Builder
@EqualsAndHashCode(callSuper = true)
@JsonNaming(value = PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonDeserialize(builder = ClearIsls.ClearIslsBuilder.class)
public class ClearIsls extends Message {
    @JsonProperty("headers")
    Headers headers;
    @JsonProperty("switch_id")
    SwitchId switchId;
}
