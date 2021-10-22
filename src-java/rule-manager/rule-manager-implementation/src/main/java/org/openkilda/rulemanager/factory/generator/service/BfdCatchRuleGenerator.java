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

import static org.openkilda.model.cookie.Cookie.CATCH_BFD_RULE_COOKIE;
import static org.openkilda.rulemanager.Constants.Priority.CATCH_BFD_RULE_PRIORITY;

import org.openkilda.model.Switch;
import org.openkilda.model.SwitchFeature;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.rulemanager.Constants;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.FlowSpeakerCommandData;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.OfVersion;
import org.openkilda.rulemanager.ProtoConstants.EthType;
import org.openkilda.rulemanager.ProtoConstants.IpProto;
import org.openkilda.rulemanager.ProtoConstants.PortNumber;
import org.openkilda.rulemanager.ProtoConstants.PortNumber.SpecialPortType;
import org.openkilda.rulemanager.SpeakerCommandData;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.factory.RuleGenerator;
import org.openkilda.rulemanager.match.FieldMatch;

import com.google.common.collect.Sets;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class BfdCatchRuleGenerator implements RuleGenerator {

    @Override
    public List<SpeakerCommandData> generateCommands(Switch sw) {
        if (!sw.getFeatures().contains(SwitchFeature.BFD)) {
            return Collections.emptyList();
        }

        Set<FieldMatch> match = catchRuleMatch(sw);
        Instructions instructions = Instructions.builder()
                .applyActions(Collections.singletonList(new PortOutAction(new PortNumber(SpecialPortType.LOCAL))))
                .build();
        return Collections.singletonList(FlowSpeakerCommandData.builder()
                        .switchId(sw.getSwitchId())
                        .ofVersion(OfVersion.of(sw.getOfVersion()))
                        .cookie(new Cookie(CATCH_BFD_RULE_COOKIE))
                        .table(OfTable.INPUT)
                        .priority(CATCH_BFD_RULE_PRIORITY)
                        .match(match)
                        .instructions(instructions)
                .build());
    }

    private static Set<FieldMatch> catchRuleMatch(Switch sw) {
        return Sets.newHashSet(
                FieldMatch.builder().field(Field.ETH_DST).value(sw.getSwitchId().toLong()).build(),
                FieldMatch.builder().field(Field.ETH_TYPE).value(EthType.IPv4).build(),
                FieldMatch.builder().field(Field.IP_PROTO).value(IpProto.UDP).build(),
                FieldMatch.builder().field(Field.UDP_DST).value(Constants.BDF_DEFAULT_PORT).build()
        );
    }
}
