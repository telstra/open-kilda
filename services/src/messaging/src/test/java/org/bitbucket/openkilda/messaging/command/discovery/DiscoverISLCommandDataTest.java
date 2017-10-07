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

package org.bitbucket.openkilda.messaging.command.discovery;

import static org.bitbucket.openkilda.messaging.command.Constants.outputPort;
import static org.bitbucket.openkilda.messaging.command.Constants.switchId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import org.bitbucket.openkilda.messaging.command.discovery.DiscoverIslCommandData;

import org.junit.Test;

public class DiscoverISLCommandDataTest {
    @Test
    public void toStringTest() throws Exception {
        DiscoverIslCommandData data = new DiscoverIslCommandData();
        data.setSwitchId(switchId);
        String dataString = data.toString();
        assertNotNull(dataString);
        assertFalse(dataString.isEmpty());
    }

    @Test
    public void switchId() throws Exception {
        DiscoverIslCommandData data = new DiscoverIslCommandData();
        data.setSwitchId(switchId);
        assertEquals(switchId, data.getSwitchId());
    }

    @Test
    public void portNo() throws Exception {
        DiscoverIslCommandData data = new DiscoverIslCommandData();
        data.setPortNo(outputPort);
        assertEquals(outputPort, data.getPortNo());
    }
}
