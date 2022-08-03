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

package org.openkilda.wfm.topology.switchmanager.model.v2;

import org.openkilda.messaging.info.switches.v2.LogicalPortInfoEntryV2;
import org.openkilda.messaging.info.switches.v2.MisconfiguredInfo;

import lombok.Value;

import java.util.ArrayList;
import java.util.List;

@Value
public class ValidateLogicalPortsResultV2 {
    boolean asExpected;
    List<LogicalPortInfoEntryV2> missingLogicalPorts;
    List<LogicalPortInfoEntryV2> properLogicalPorts;
    List<LogicalPortInfoEntryV2> excessLogicalPorts;
    List<MisconfiguredInfo<LogicalPortInfoEntryV2>> misconfiguredLogicalPorts;
    String errorMessage; //TODO: for api v1 compatibility, will be removed in the future

    public static ValidateLogicalPortsResultV2 newEmpty() {
        return new ValidateLogicalPortsResultV2(false, new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>(), new ArrayList<>(), "");
    }

    public static ValidateLogicalPortsResultV2 newErrorMessage(String error) {
        return new ValidateLogicalPortsResultV2(false, new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>(), new ArrayList<>(), error);
    }
}
