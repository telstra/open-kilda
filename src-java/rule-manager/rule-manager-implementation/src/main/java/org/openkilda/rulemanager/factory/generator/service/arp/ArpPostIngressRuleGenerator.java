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

import static org.openkilda.model.cookie.Cookie.ARP_POST_INGRESS_COOKIE;

import org.openkilda.model.Switch;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.rulemanager.Constants.Priority;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.RuleManagerConfig;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.match.FieldMatch;
import org.openkilda.rulemanager.utils.RoutingMetadata;

import com.google.common.collect.Sets;
import lombok.Builder;

import java.util.List;
import java.util.Set;

public class ArpPostIngressRuleGenerator extends ArpRuleGenerator {

    @Builder
    public ArpPostIngressRuleGenerator(RuleManagerConfig config) {
        super(config);
    }

    @Override
    public List<SpeakerData> generateCommands(Switch sw) {
        RoutingMetadata metadata = buildMetadata(RoutingMetadata.builder().arpFlag(true), sw);
        Set<FieldMatch> match = Sets.newHashSet(
                FieldMatch.builder().field(Field.METADATA).value(metadata.getValue()).mask(metadata.getMask()).build()
        );

        Instructions instructions = buildSendToControllerInstructions();
        Cookie cookie = new Cookie(ARP_POST_INGRESS_COOKIE);

        return buildCommands(sw, cookie, OfTable.POST_INGRESS, Priority.ARP_POST_INGRESS_PRIORITY,
                match, instructions);
    }
}
