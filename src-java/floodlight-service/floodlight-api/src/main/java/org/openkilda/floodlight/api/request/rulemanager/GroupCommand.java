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

package org.openkilda.floodlight.api.request.rulemanager;

import org.openkilda.model.SwitchId;
import org.openkilda.rulemanager.GroupSpeakerData;

import lombok.Value;

@Value
public class GroupCommand extends OfCommand {

    private final GroupSpeakerData data;


    @Override
    public void buildInstall(OfEntityBatch builder, SwitchId switchId) {
        builder.addInstallGroup(data, switchId);
    }

    @Override
    public void buildDelete(OfEntityBatch builder, SwitchId switchId) {
        builder.addDeleteGroup(data, switchId);
    }
}
