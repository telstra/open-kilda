/* Copyright 2019 Telstra Open Source
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

package org.openkilda.floodlight.feature;

import org.openkilda.model.SwitchFeature;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.SwitchDescription;

import java.util.Optional;

public class NoviFlowPushPopVxlanFeature extends AbstractFeature {

    @Override
    public Optional<SwitchFeature> discover(IOFSwitch sw) {
        SwitchDescription description = sw.getSwitchDescription();
        if (description == null || description.getManufacturerDescription() == null) {
            return Optional.empty();
        }

        if (E_SWITCH_MANUFACTURER_DESCRIPTION.equalsIgnoreCase(description.getManufacturerDescription())) {
            return Optional.of(SwitchFeature.NOVIFLOW_PUSH_POP_VXLAN);
        }

        if (E_SWITCH_HARDWARE_DESCRIPTION_REGEX.matcher(description.getHardwareDescription()).matches()) {
            return Optional.of(SwitchFeature.NOVIFLOW_PUSH_POP_VXLAN);
        }

        if (NOVIFLOW_VIRTUAL_SWITCH_HARDWARE_DESCRIPTION_REGEX.matcher(
                description.getHardwareDescription()).matches()) {
            return Optional.of(SwitchFeature.NOVIFLOW_PUSH_POP_VXLAN);
        }

        if (description.getManufacturerDescription().toLowerCase().contains(NOVIFLOW_MANUFACTURER_SUFFIX)) {
            return Optional.of(SwitchFeature.NOVIFLOW_PUSH_POP_VXLAN);
        }
        return Optional.empty();
    }
}
