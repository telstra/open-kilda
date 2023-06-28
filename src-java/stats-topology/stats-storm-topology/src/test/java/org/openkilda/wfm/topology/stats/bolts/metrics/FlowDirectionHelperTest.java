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

import org.openkilda.wfm.topology.stats.bolts.metrics.FlowDirectionHelper.Direction;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


public class FlowDirectionHelperTest {
    private static final long FORWARD_COOKIE = 0x4000000000000001L;
    private static final long REVERSE_COOKIE = 0x2000000000000001L;
    private static final long BAD_COOKIE = 0x6000000000000001L;

    @Test
    public void findDirectionTest() throws Exception {
        Assertions.assertEquals(Direction.FORWARD, FlowDirectionHelper.findDirection(FORWARD_COOKIE));
        Assertions.assertEquals(Direction.REVERSE, FlowDirectionHelper.findDirection(REVERSE_COOKIE));

        Assertions.assertThrows(FlowCookieException.class, () -> {
            FlowDirectionHelper.findDirection(BAD_COOKIE);
        });
    }
}
