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

package org.openkilda.persistence.dummy;

import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.SwitchProperties;

import lombok.Data;

import java.util.Collections;
import java.util.Set;

@Data
public class SwitchPropertiesDefaults {
    private Set<FlowEncapsulationType> supportedTransitEncapsulation = Collections.singleton(
            FlowEncapsulationType.TRANSIT_VLAN);

    private boolean multiTable = false;
    private boolean switchLldp = false;

    /**
     * Populate {@link SwitchProperties} object with defaults.
     */
    public SwitchProperties.SwitchPropertiesBuilder fill(SwitchProperties.SwitchPropertiesBuilder properties) {
        properties.supportedTransitEncapsulation(supportedTransitEncapsulation);
        properties.multiTable(multiTable);
        properties.switchLldp(switchLldp);

        return properties;
    }
}
