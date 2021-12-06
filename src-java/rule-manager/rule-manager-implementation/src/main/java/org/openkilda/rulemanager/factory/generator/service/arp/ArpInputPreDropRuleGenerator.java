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

package org.openkilda.rulemanager.factory.generator.service.arp;

import static org.openkilda.model.cookie.Cookie.ARP_INPUT_PRE_DROP_COOKIE;

import org.openkilda.model.Switch;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.rulemanager.Constants.Priority;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.ProtoConstants.EthType;
import org.openkilda.rulemanager.RuleManagerConfig;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.match.FieldMatch;

import com.google.common.collect.Sets;
import lombok.Builder;

import java.util.List;
import java.util.Set;

public class ArpInputPreDropRuleGenerator extends ArpRuleGenerator {

    @Builder
    public ArpInputPreDropRuleGenerator(RuleManagerConfig config) {
        super(config);
    }

    @Override
    public List<SpeakerData> generateCommands(Switch sw) {
        Set<FieldMatch> match = Sets.newHashSet(
                FieldMatch.builder().field(Field.ETH_TYPE).value(EthType.ARP).build()
        );

        Instructions instructions = buildSendToControllerInstructions();

        Cookie cookie = new Cookie(ARP_INPUT_PRE_DROP_COOKIE);
        return buildCommands(sw, cookie, OfTable.INPUT, Priority.ARP_INPUT_PRE_DROP_PRIORITY, match, instructions);
    }
}
