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

package org.openkilda.floodlight.feature;

import org.openkilda.model.SwitchFeature;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.SwitchDescription;

import java.util.Optional;
import java.util.regex.Pattern;

public class AccurateSetOfMeterRateAndBurstSizeFeature extends AbstractFeature {
    private static final Pattern E_SWITCH_HARDWARE_DESCRIPTION_REGEX = Pattern.compile("^WB5\\d{3}-E$");
    private static final String E_SWITCH_MANUFACTURER_DESCRIPTION = "E";

    @Override
    public Optional<SwitchFeature> discover(IOFSwitch sw) {
        SwitchDescription description = sw.getSwitchDescription();
        if (description != null && isNoviflowESwitch(description)) {
            return  Optional.empty();
        }

        return Optional.of(SwitchFeature.ACCURATE_SET_OF_METER_RATE_AND_BURST_SIZE);
    }

    private static boolean isNoviflowESwitch(SwitchDescription description) {
        return E_SWITCH_MANUFACTURER_DESCRIPTION.equalsIgnoreCase(description.getManufacturerDescription())
                || (description.getHardwareDescription() != null
                && E_SWITCH_HARDWARE_DESCRIPTION_REGEX.matcher(description.getHardwareDescription()).matches());
    }

}
