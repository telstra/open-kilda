/* Copyright 2017 Telstra Open Source
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

package org.openkilda.messaging.command.discovery;

import static org.openkilda.messaging.command.Constants.inputPort;
import static org.openkilda.messaging.command.Constants.switchId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import org.openkilda.messaging.command.discovery.DiscoverPathCommandData;

import org.junit.Test;

public class DiscoverPathCommandDataTest {
    @Test
    public void toStringTest() throws Exception {
        final DiscoverPathCommandData data = new DiscoverPathCommandData();
        data.setSrcPortNo(inputPort);
        String dataString = data.toString();
        assertNotNull(dataString);
        assertFalse(dataString.isEmpty());
    }

    @Test
    public void srcSwitchId() throws Exception {
        DiscoverPathCommandData data = new DiscoverPathCommandData();
        data.setSrcSwitchId(switchId);
        assertEquals(switchId, data.getSrcSwitchId());
    }

    @Test
    public void srcPortNo() throws Exception {
        DiscoverPathCommandData data = new DiscoverPathCommandData();
        data.setSrcPortNo(inputPort);
        assertEquals(inputPort, data.getSrcPortNo());
    }

    @Test
    public void dstSwitchId() throws Exception {
        DiscoverPathCommandData data = new DiscoverPathCommandData();
        data.setDstSwitchId(switchId);
        assertEquals(switchId, data.getDstSwitchId());
    }

}
