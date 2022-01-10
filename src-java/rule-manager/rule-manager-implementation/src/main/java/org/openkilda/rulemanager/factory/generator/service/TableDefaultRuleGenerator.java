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

package org.openkilda.rulemanager.factory.generator.service;

import org.openkilda.model.Switch;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.rulemanager.Constants.Priority;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.OfVersion;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.factory.RuleGenerator;

import lombok.Builder;

import java.util.Collections;
import java.util.List;

@Builder
public class TableDefaultRuleGenerator implements RuleGenerator {

    private Cookie cookie;
    private OfTable ofTable;

    @Override
    public List<SpeakerData> generateCommands(Switch sw) {
        OfVersion ofVersion = OfVersion.of(sw.getOfVersion());

        SpeakerData command = FlowSpeakerData.builder()
                .switchId(sw.getSwitchId())
                .ofVersion(ofVersion)
                .cookie(cookie)
                .table(ofTable)
                // todo move to priority 0
                .priority(Priority.MINIMAL_POSITIVE_PRIORITY)
                .build();
        return Collections.singletonList(command);
    }
}

