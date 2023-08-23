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

import static org.openkilda.model.SwitchFeature.METERS;
import static org.openkilda.model.SwitchFeature.PKTPS_FLAG;
import static org.openkilda.model.cookie.Cookie.LLDP_TRANSIT_COOKIE;
import static org.openkilda.rulemanager.Utils.getMatchByField;

import org.openkilda.model.cookie.Cookie;
import org.openkilda.rulemanager.Constants.Priority;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.ProtoConstants.EthType;
import org.openkilda.rulemanager.match.FieldMatch;

import com.google.common.collect.Sets;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;

import java.util.Set;

public class LldpTransitRuleGeneratorTest extends LldpRuleGeneratorTest {

    @BeforeEach
    public void setup() {
        config = prepareConfig();

        generator = LldpTransitRuleGenerator.builder()
                .config(config)
                .build();

        cookie = new Cookie(LLDP_TRANSIT_COOKIE);
        table = OfTable.TRANSIT;
        priority = Priority.LLDP_TRANSIT_ISL_PRIORITY;

        expectedFeatures = Sets.newHashSet(METERS, PKTPS_FLAG);
    }

    @Override
    protected void checkMatch(Set<FieldMatch> match) {
        Assertions.assertEquals(1, match.size());
        FieldMatch ethTypeMatch = getMatchByField(Field.ETH_TYPE, match);
        Assertions.assertEquals(EthType.LLDP, ethTypeMatch.getValue());
        Assertions.assertFalse(ethTypeMatch.isMasked());
    }
}
