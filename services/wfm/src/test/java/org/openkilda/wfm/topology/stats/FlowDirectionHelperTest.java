/* Copyright 2018 Telstra Open Source
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

package org.openkilda.wfm.topology.stats;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.openkilda.wfm.topology.stats.FlowDirectionHelper.Direction;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.Map;

public class FlowDirectionHelperTest {
    private static final long LEGACY_FORWARD_COOKIE = 0x10400000005d803L;
    private static final long LEGACY_REVERSE_COOKIE = 0x18400000005d803L;
    private static final long FORWARD_COOKIE = 0x4000000000000001L;
    private static final long REVERSE_COOKIE = 0x2000000000000001L;
    private static final long BAD_COOKIE =     0x235789abcd432425L;
    private static final String FLOW_ID = "f3459085345454";
    private static final String SRC_SWITCH = "de:ad:be:ef:00:00:00:02";
    private static final String DST_SWITCH = "de:ad:be:ef:00:00:00:04";

    private FlowDirectionHelper flowDirectionHelper;
    private static Map<String, Object> queryMap;

    @Rule
    public ExpectedException thrown = ExpectedException.none();


    @Test
    public void isLegacyCookieTest() {
        assertTrue(FlowDirectionHelper.isLegacyCookie(LEGACY_FORWARD_COOKIE));
        assertTrue(FlowDirectionHelper.isLegacyCookie(LEGACY_REVERSE_COOKIE));
        assertFalse(FlowDirectionHelper.isLegacyCookie(FORWARD_COOKIE));
        assertFalse(FlowDirectionHelper.isLegacyCookie(REVERSE_COOKIE));

        assertFalse(FlowDirectionHelper.isLegacyCookie(BAD_COOKIE));
    }

    @Test
    public void isKildaCookieTest() {
        assertTrue(FlowDirectionHelper.isKildaCookie(FORWARD_COOKIE));
        assertTrue(FlowDirectionHelper.isKildaCookie(REVERSE_COOKIE));
        assertFalse(FlowDirectionHelper.isKildaCookie(LEGACY_FORWARD_COOKIE));
        assertFalse(FlowDirectionHelper.isKildaCookie(LEGACY_REVERSE_COOKIE));
        assertFalse(FlowDirectionHelper.isKildaCookie(BAD_COOKIE));
    }

    @Test
    public void getKildaDirectionTest() throws Exception {
        assertEquals(Direction.FORWARD, FlowDirectionHelper.getKildaDirection(FORWARD_COOKIE));
        assertEquals(Direction.REVERSE, FlowDirectionHelper.getKildaDirection(REVERSE_COOKIE));

        thrown.expect(Exception.class);
        thrown.expectMessage(LEGACY_FORWARD_COOKIE + " is not a Kilda flow");
        FlowDirectionHelper.getKildaDirection(LEGACY_FORWARD_COOKIE);
    }

    @Test
    public void getLegacyDirectionTest() throws Exception {
        assertEquals(Direction.FORWARD, FlowDirectionHelper.getLegacyDirection(LEGACY_FORWARD_COOKIE));
        assertEquals(Direction.REVERSE, FlowDirectionHelper.getLegacyDirection(LEGACY_REVERSE_COOKIE));

        thrown.expect(Exception.class);
        thrown.expectMessage(FORWARD_COOKIE +  " is not a legacy flow");
        FlowDirectionHelper.getLegacyDirection(FORWARD_COOKIE);
    }

    @Test
    public void findDirectionTest() throws Exception {
        assertEquals(Direction.FORWARD, FlowDirectionHelper.findDirection(LEGACY_FORWARD_COOKIE));
        assertEquals(Direction.REVERSE, FlowDirectionHelper.findDirection(REVERSE_COOKIE));

        thrown.expect(Exception.class);
        thrown.expectMessage(BAD_COOKIE + " is not a Kilda flow");
        FlowDirectionHelper.findDirection(BAD_COOKIE);
    }
}
