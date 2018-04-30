/*
 * Copyright 2017 Telstra Open Source
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openkilda.floodlight;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.MacAddress;

import java.util.Arrays;

public class SwitchUtils {
    private final IOFSwitchService switchService;

    public SwitchUtils(IOFSwitchService switchService) {
        this.switchService = switchService;
    }

    public IOFSwitch lookupSwitch(DatapathId dpId) {
        IOFSwitch swInfo = switchService.getSwitch(dpId);
        if (swInfo == null) {
            throw new IllegalArgumentException(String.format("Switch %s not found", dpId));
        }
        return swInfo;
    }

    public MacAddress dpIdToMac(final IOFSwitch sw) {
        return this.dpIdToMac(sw.getId());
    }

    public MacAddress dpIdToMac(final DatapathId dpId) {
        return MacAddress.of(Arrays.copyOfRange(dpId.getBytes(), 2, 8));
    }
}
