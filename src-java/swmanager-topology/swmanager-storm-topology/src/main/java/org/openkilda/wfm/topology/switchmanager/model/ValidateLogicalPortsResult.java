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

package org.openkilda.wfm.topology.switchmanager.model;

import org.openkilda.messaging.info.switches.LogicalPortInfoEntry;

import lombok.Value;

import java.util.ArrayList;
import java.util.List;

@Value
public class ValidateLogicalPortsResult {
    public static ValidateLogicalPortsResult newEmpty() {
        return new ValidateLogicalPortsResult(
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), "");
    }

    public static ValidateLogicalPortsResult newErrorMessage(String error) {
        return new ValidateLogicalPortsResult(
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), error);
    }

    List<LogicalPortInfoEntry> properLogicalPorts;
    List<LogicalPortInfoEntry> missingLogicalPorts;
    List<LogicalPortInfoEntry> excessLogicalPorts;
    List<LogicalPortInfoEntry> misconfiguredLogicalPorts;
    String errorMessage;
}
