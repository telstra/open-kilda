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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.openkilda.model.SwitchFeature.METERS;
import static org.openkilda.model.SwitchFeature.PKTPS_FLAG;
import static org.openkilda.model.cookie.Cookie.ARP_POST_INGRESS_COOKIE;
import static org.openkilda.rulemanager.Utils.getMatchByField;

import org.openkilda.model.cookie.Cookie;
import org.openkilda.rulemanager.Constants.Priority;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.match.FieldMatch;
import org.openkilda.rulemanager.utils.RoutingMetadata;

import com.google.common.collect.Sets;
import org.junit.Before;

import java.util.Set;

public class ArpPostIngressRuleGeneratorTest extends ArpRuleGeneratorTest {

    @Before
    public void setup() {
        config = prepareConfig();

        generator = ArpPostIngressRuleGenerator.builder()
                .config(config)
                .build();

        cookie = new Cookie(ARP_POST_INGRESS_COOKIE);
        table = OfTable.POST_INGRESS;
        priority = Priority.ARP_POST_INGRESS_PRIORITY;

        expectedFeatures = Sets.newHashSet(METERS, PKTPS_FLAG);
    }

    @Override
    protected void checkMatch(Set<FieldMatch> match) {
        assertEquals(1, match.size());
        FieldMatch metadataMatch = getMatchByField(Field.METADATA, match);
        RoutingMetadata expectedMetadata = RoutingMetadata.builder().arpFlag(true).build(sw.getFeatures());
        assertEquals(expectedMetadata.getValue(), metadataMatch.getValue());
        assertTrue(metadataMatch.isMasked());
        assertEquals(expectedMetadata.getMask(), metadataMatch.getMask().longValue());
    }
}
