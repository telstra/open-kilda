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

package org.openkilda.wfm.topology.stats.bolts.metrics;

import static org.junit.Assert.assertEquals;

import org.openkilda.wfm.topology.stats.bolts.metrics.FlowDirectionHelper.Direction;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class FlowDirectionHelperTest {
    private static final long FORWARD_COOKIE = 0x4000000000000001L;
    private static final long REVERSE_COOKIE = 0x2000000000000001L;
    private static final long BAD_COOKIE =     0x6000000000000001L;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void findDirectionTest() throws Exception {
        assertEquals(Direction.FORWARD, FlowDirectionHelper.findDirection(FORWARD_COOKIE));
        assertEquals(Direction.REVERSE, FlowDirectionHelper.findDirection(REVERSE_COOKIE));

        thrown.expect(FlowCookieException.class);
        FlowDirectionHelper.findDirection(BAD_COOKIE);
    }
}
