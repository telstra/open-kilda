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

package org.openkilda.floodlight.feature;

import net.floodlightcontroller.core.IOFSwitch;
import org.projectfloodlight.openflow.protocol.OFVersion;

import java.util.Optional;

public abstract class KildaOvsFeature extends AbstractFeature {

    static boolean isKildaOvs13(IOFSwitch sw) {
        if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_13) != 0 || sw.getSwitchDescription() == null) {
            return false;
        }
        String softwareDescription = Optional.of(sw.getSwitchDescription().getSoftwareDescription()).orElse("");
        return MANUFACTURER_NICIRA.equals(sw.getSwitchDescription().getManufacturerDescription())
                && softwareDescription.contains(KILDA_OVS_SOFTWARE_DESCRIPTION_SUFFIX);
    }
}
