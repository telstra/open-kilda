/* Copyright 2019 Telstra Open Source
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

package org.openkilda.model;

import org.junit.Assert;
import org.junit.Test;

public class MeterIdTest {

    @Test
    public void createMeterIdForValidDefaultRuleTest() {
        for (long id = MeterId.MIN_SYSTEM_RULE_METER_ID; id <= MeterId.MAX_SYSTEM_RULE_METER_ID; id++) {
            long cookie = Cookie.createCookieForDefaultRule(id).getValue();
            Assert.assertEquals(id, MeterId.createMeterIdForDefaultRule(cookie).getValue());
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void createMeterIdForInvalidDefaultRuleTest() {
        MeterId.createMeterIdForDefaultRule(0x0000000000000123L);
    }
}
