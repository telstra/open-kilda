/* Copyright 2023 Telstra Open Source
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

package org.openkilda.wfm.topology.stats.model;

import org.openkilda.messaging.info.stats.GroupStatsEntry;
import org.openkilda.model.SwitchId;

import lombok.EqualsAndHashCode;
import lombok.Value;

import java.util.ArrayList;
import java.util.List;

@Value
@EqualsAndHashCode(callSuper = true)
public class SwitchGroupStats extends BaseSwitchStats {
    List<GroupStatsAndDescriptor> statsEntries = new ArrayList<>();

    public SwitchGroupStats(SwitchId switchId) {
        super(switchId);
    }

    public void add(GroupStatsEntry data, KildaEntryDescriptor descriptor) {
        statsEntries.add(new GroupStatsAndDescriptor(data, descriptor));
    }
}
