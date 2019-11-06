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

import static org.apache.commons.lang3.StringUtils.containsIgnoreCase;

import org.openkilda.model.SwitchFeature;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.SwitchDescription;

import java.util.Optional;

public class PktpsFlagFeature extends AbstractFeature {

    @Override
    public Optional<SwitchFeature> discover(IOFSwitch sw) {
        Optional<SwitchFeature> empty = Optional.empty();
        SwitchDescription description = sw.getSwitchDescription();

        if (description.getHardwareDescription() == null) {
            return empty;
        }

        if (containsIgnoreCase(description.getManufacturerDescription(), CENTEC_MANUFACTURED)) {
            return empty;
        }

        if (E_SWITCH_MANUFACTURER_DESCRIPTION.equalsIgnoreCase(description.getManufacturerDescription())
                || E_SWITCH_HARDWARE_DESCRIPTION_REGEX.matcher(description.getHardwareDescription()).matches()
                || NOVIFLOW_VIRTUAL_SWITCH_HARDWARE_DESCRIPTION_REGEX.matcher(
                        description.getHardwareDescription()).matches()) {
            // Actually E switches support PKTPS flag. But they can't work with PKTPS and KBPS flags in the same time.
            // KBPS flag is used for Flow creation so we must enable it to be able to create Flows on E switches.
            // PKTPS is used for default rule meters and it can be replaced by KBPS (with recalculated rate and
            // burst size). That is why we will assume that E switches don't support PKTPS flag.
            return empty;
        }

        return Optional.of(SwitchFeature.PKTPS_FLAG);
    }
}
