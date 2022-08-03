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

import org.openkilda.messaging.info.switches.v2.GroupInfoEntryV2;
import org.openkilda.messaging.info.switches.v2.MisconfiguredInfo;

import lombok.Value;

import java.util.List;

@Value
public class ValidateGroupsResultV2 {
    boolean asExpected;
    List<GroupInfoEntryV2> missingGroups;
    List<GroupInfoEntryV2> properGroups;
    List<GroupInfoEntryV2> excessGroups;
    List<MisconfiguredInfo<GroupInfoEntryV2>> misconfiguredGroups;
}
