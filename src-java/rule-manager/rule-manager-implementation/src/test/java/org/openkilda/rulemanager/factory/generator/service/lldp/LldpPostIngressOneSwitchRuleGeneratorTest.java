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

package org.openkilda.rulemanager.factory.generator.service.lldp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openkilda.model.SwitchFeature.METERS;
import static org.openkilda.model.SwitchFeature.PKTPS_FLAG;
import static org.openkilda.model.cookie.Cookie.LLDP_POST_INGRESS_ONE_SWITCH_COOKIE;
import static org.openkilda.rulemanager.Utils.getMatchByField;

import org.openkilda.model.cookie.Cookie;
import org.openkilda.rulemanager.Constants.Priority;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.match.FieldMatch;
import org.openkilda.rulemanager.utils.RoutingMetadata;

import com.google.common.collect.Sets;
import org.junit.jupiter.api.BeforeEach;

import java.util.Set;

public class LldpPostIngressOneSwitchRuleGeneratorTest extends LldpRuleGeneratorTest {

    @BeforeEach
    public void setup() {
        config = prepareConfig();

        generator = LldpPostIngressOneSwitchRuleGenerator.builder()
                .config(config)
                .build();

        cookie = new Cookie(LLDP_POST_INGRESS_ONE_SWITCH_COOKIE);
        table = OfTable.POST_INGRESS;
        priority = Priority.LLDP_POST_INGRESS_ONE_SWITCH_PRIORITY;

        expectedFeatures = Sets.newHashSet(METERS, PKTPS_FLAG);
    }

    @Override
    protected void checkMatch(Set<FieldMatch> match) {
        assertEquals(1, match.size());
        FieldMatch metadataMatch = getMatchByField(Field.METADATA, match);
        RoutingMetadata expectedMetadata = RoutingMetadata.builder()
                .lldpFlag(true)
                .oneSwitchFlowFlag(true)
                .build(sw.getFeatures());
        assertEquals(expectedMetadata.getValue(), metadataMatch.getValue());
        assertTrue(metadataMatch.isMasked());
        assertEquals(expectedMetadata.getMask(), metadataMatch.getMask().longValue());
    }
}
