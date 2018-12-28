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

package org.openkilda.messaging.command.flow;

import static org.openkilda.messaging.command.Constants.flowName;
import static org.openkilda.messaging.command.Constants.inputPort;
import static org.openkilda.messaging.command.Constants.outputPort;
import static org.openkilda.messaging.command.Constants.switchId;
import static org.openkilda.messaging.command.Constants.transitVlanId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

import java.util.UUID;

public class InstallTransitFlowTest {
    private InstallTransitFlow flow = new InstallTransitFlow(UUID.randomUUID(),
            flowName, 0L, switchId, inputPort, outputPort, transitVlanId);

    @Test
    public void toStringTest() throws Exception {
        String flowString = flow.toString();
        assertNotNull(flowString);
        assertFalse(flowString.isEmpty());
    }

    @Test
    public void getTransitVlanId() throws Exception {
        assertEquals(transitVlanId, flow.getTransitVlanId().intValue());
    }

    @Test
    public void setTransitVlanId() throws Exception {
        flow.setTransitVlanId(transitVlanId);
        assertEquals(transitVlanId, flow.getTransitVlanId().intValue());
    }

    @Test(expected = IllegalArgumentException.class)
    public void setNullTransitVlanId() throws Exception {
        flow.setTransitVlanId(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setZeroTransitVlanId() throws Exception {
        flow.setTransitVlanId(0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setTooBigTransitVlanId() throws Exception {
        flow.setTransitVlanId(4096);
    }
}
