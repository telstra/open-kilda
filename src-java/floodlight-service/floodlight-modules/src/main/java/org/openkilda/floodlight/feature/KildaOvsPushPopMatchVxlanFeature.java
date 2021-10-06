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

import org.openkilda.model.SwitchFeature;

import net.floodlightcontroller.core.IOFSwitch;
import org.apache.maven.artifact.versioning.ComparableVersion;

import java.util.Optional;

public class KildaOvsPushPopMatchVxlanFeature extends KildaOvsFeature {
    public static final ComparableVersion KILDA_VXLAN_MIN_VERSION = new ComparableVersion("2.15.1.1-kilda");

    @Override
    public Optional<SwitchFeature> discover(IOFSwitch sw) {
        if (!isKildaOvs13(sw)) {
            return Optional.empty();
        }
        ComparableVersion version = new ComparableVersion(sw.getSwitchDescription().getSoftwareDescription());
        if (KILDA_VXLAN_MIN_VERSION.compareTo(version) <= 0) {
            return Optional.of(SwitchFeature.KILDA_OVS_PUSH_POP_MATCH_VXLAN);
        }
        return Optional.empty();
    }
}
