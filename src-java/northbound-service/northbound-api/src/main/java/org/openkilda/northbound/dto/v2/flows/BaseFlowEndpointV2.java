/* Copyright 2023 Telstra Open Source
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

package org.openkilda.northbound.dto.v2.flows;

import static org.openkilda.messaging.Utils.MAX_VLAN_ID;
import static org.openkilda.messaging.Utils.MIN_VLAN_ID;

import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.PositiveOrZero;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonNaming(SnakeCaseStrategy.class)
public class BaseFlowEndpointV2 {

    @NonNull
    protected SwitchId switchId;

    @NonNull
    @PositiveOrZero(message = "portNumber can't be negative")
    protected Integer portNumber;

    @Min(value = MIN_VLAN_ID, message = "vlanId can't be negative")
    @Max(value = MAX_VLAN_ID, message = "vlanId can't be greater than " + MAX_VLAN_ID)
    protected int vlanId;

    @Min(value = MIN_VLAN_ID, message = "innerVlanId can't be negative")
    @Max(value = MAX_VLAN_ID, message = "innerVlanId can't be greater than " + MAX_VLAN_ID)
    protected int innerVlanId;
}

