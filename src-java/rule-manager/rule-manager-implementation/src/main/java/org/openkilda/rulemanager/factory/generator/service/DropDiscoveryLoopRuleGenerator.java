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

import static org.openkilda.model.cookie.Cookie.DROP_VERIFICATION_LOOP_RULE_COOKIE;
import static org.openkilda.rulemanager.Constants.Priority.DROP_DISCOVERY_LOOP_RULE_PRIORITY;
import static org.openkilda.rulemanager.OfVersion.OF_12;

import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.FlowSpeakerCommandData;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.OfVersion;
import org.openkilda.rulemanager.RuleManagerConfig;
import org.openkilda.rulemanager.SpeakerCommandData;
import org.openkilda.rulemanager.factory.RuleGenerator;
import org.openkilda.rulemanager.match.FieldMatch;

import com.google.common.collect.Sets;
import lombok.Builder;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@Builder
public class DropDiscoveryLoopRuleGenerator implements RuleGenerator {

    private RuleManagerConfig config;

    @Override
    public List<SpeakerCommandData> generateCommands(Switch sw) {
        OfVersion ofVersion = OfVersion.of(sw.getOfVersion());
        if (ofVersion == OF_12) {
            return Collections.emptyList();
        }

        long ethDst = new SwitchId(config.getDiscoveryBcastPacketDst()).toLong();
        Set<FieldMatch> match = Sets.newHashSet(
                FieldMatch.builder().field(Field.ETH_DST).value(ethDst).build(),
                FieldMatch.builder().field(Field.ETH_SRC).value(sw.getSwitchId().toLong()).build()
        );

        return Collections.singletonList(FlowSpeakerCommandData.builder()
                        .switchId(sw.getSwitchId())
                        .ofVersion(ofVersion)
                        .cookie(new Cookie(DROP_VERIFICATION_LOOP_RULE_COOKIE))
                        .table(OfTable.INPUT)
                        .priority(DROP_DISCOVERY_LOOP_RULE_PRIORITY)
                        .match(match)
                .build());
    }
}
