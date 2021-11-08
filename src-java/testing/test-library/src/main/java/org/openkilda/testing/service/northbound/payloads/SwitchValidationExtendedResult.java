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

package org.openkilda.testing.service.northbound.payloads;

import org.openkilda.model.SwitchId;
import org.openkilda.northbound.dto.v1.switches.SwitchValidationResult;

import lombok.EqualsAndHashCode;
import lombok.ToString;

@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class SwitchValidationExtendedResult extends SwitchValidationResult {
    SwitchId switchId;

    public SwitchValidationExtendedResult(SwitchId swId, SwitchValidationResult validationResult) {
        super(validationResult.getRules(), validationResult.getMeters(), validationResult.getGroups(),
                validationResult.getLogicalPorts());
        this.switchId = swId;
    }
}
