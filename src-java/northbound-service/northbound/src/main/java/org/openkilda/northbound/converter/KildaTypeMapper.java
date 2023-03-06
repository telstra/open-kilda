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

package org.openkilda.northbound.converter;

import org.openkilda.model.MacAddress;
import org.openkilda.model.SwitchId;

import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public abstract class KildaTypeMapper {
    /**
     * Convert {@link String} into {@link SwitchId}.
     */
    public SwitchId mapSwitchId(String switchId) {
        if (switchId == null) {
            return null;
        }
        return new SwitchId(switchId);
    }

    /**
     * Convert {@link SwitchId} into {@link String}.
     */
    public String mapSwitchId(SwitchId switchId) {
        if (switchId == null) {
            return null;
        }
        return switchId.toString();
    }

    /**
     * Convert {@link String} into {@link MacAddress}.
     */
    public MacAddress mapMacAddress(String macAddress) {
        if (macAddress == null) {
            return null;
        }
        return new MacAddress(macAddress);
    }

    /**
     * Convert {@link MacAddress} into {@link String}.
     */
    public String mapMacAddress(MacAddress macAddress) {
        if (macAddress == null) {
            return null;
        }
        return macAddress.toString();
    }
}
