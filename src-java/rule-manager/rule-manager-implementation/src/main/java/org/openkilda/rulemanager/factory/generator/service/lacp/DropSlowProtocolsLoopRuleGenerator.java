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

package org.openkilda.rulemanager.factory.generator.service.lacp;

import static org.openkilda.rulemanager.Constants.Priority.DROP_LOOP_SLOW_PROTOCOLS_PRIORITY;
import static org.openkilda.rulemanager.OfVersion.OF_12;

import org.openkilda.model.MacAddress;
import org.openkilda.model.Switch;
import org.openkilda.model.cookie.ServiceCookie;
import org.openkilda.model.cookie.ServiceCookie.ServiceCookieTag;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.OfVersion;
import org.openkilda.rulemanager.ProtoConstants.EthType;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.factory.RuleGenerator;
import org.openkilda.rulemanager.match.FieldMatch;

import com.google.common.collect.Sets;
import lombok.Builder;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@Builder
public class DropSlowProtocolsLoopRuleGenerator implements RuleGenerator {

    @Override
    public List<SpeakerData> generateCommands(Switch sw) {
        OfVersion ofVersion = OfVersion.of(sw.getOfVersion());
        if (ofVersion == OF_12) {
            return Collections.emptyList();
        }

        Set<FieldMatch> match = Sets.newHashSet(
                FieldMatch.builder().field(Field.ETH_TYPE).value(EthType.SLOW_PROTOCOLS).build(),
                FieldMatch.builder().field(Field.ETH_SRC).value(sw.getSwitchId().toLong()).build(),
                FieldMatch.builder().field(Field.ETH_DST).value(MacAddress.SLOW_PROTOCOLS.toLong()).build());

        return Collections.singletonList(FlowSpeakerData.builder()
                .switchId(sw.getSwitchId())
                .ofVersion(ofVersion)
                .cookie(new ServiceCookie(ServiceCookieTag.DROP_SLOW_PROTOCOLS_LOOP_COOKIE))
                .table(OfTable.INPUT)
                .priority(DROP_LOOP_SLOW_PROTOCOLS_PRIORITY)
                .match(match)
                .instructions(Instructions.builder().build())
                .build());
    }
}
