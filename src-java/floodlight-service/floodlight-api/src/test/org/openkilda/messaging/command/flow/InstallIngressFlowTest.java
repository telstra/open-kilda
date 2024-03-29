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

package org.openkilda.messaging.command.flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.openkilda.messaging.command.Constants.bandwidth;
import static org.openkilda.messaging.command.Constants.egressSwitchId;
import static org.openkilda.messaging.command.Constants.flowName;
import static org.openkilda.messaging.command.Constants.inputPort;
import static org.openkilda.messaging.command.Constants.inputVlanId;
import static org.openkilda.messaging.command.Constants.meterId;
import static org.openkilda.messaging.command.Constants.outputPort;
import static org.openkilda.messaging.command.Constants.outputVlanType;
import static org.openkilda.messaging.command.Constants.switchId;
import static org.openkilda.messaging.command.Constants.transitEncapsulationId;
import static org.openkilda.messaging.command.Constants.transitEncapsulationType;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.UUID;

public class InstallIngressFlowTest {
    private InstallIngressFlow flow = new InstallIngressFlow(UUID.randomUUID(), flowName, 0L, switchId, inputPort,
            outputPort, inputVlanId, transitEncapsulationId, transitEncapsulationType,
            outputVlanType, bandwidth, meterId, egressSwitchId, false, false);

    @Test
    public void toStringTest() throws Exception {
        String flowString = flow.toString();
        Assertions.assertNotNull(flowString);
        Assertions.assertFalse(flowString.isEmpty());
    }

    @Test
    public void getBandwidth() throws Exception {
        assertEquals(bandwidth, flow.getBandwidth().longValue());
    }

    @Test
    public void getMeterId() throws Exception {
        assertEquals(meterId, flow.getMeterId().longValue());
    }

    @Test
    public void getInputVlanId() throws Exception {
        assertEquals(inputVlanId, flow.getInputVlanId().intValue());
    }

    @Test
    public void setBandwidth() throws Exception {
        flow.setBandwidth(bandwidth);
        assertEquals(bandwidth, flow.getBandwidth().longValue());
    }

    @Test
    public void setNullBandwidth() throws Exception {
        assertThrows(IllegalArgumentException.class,()-> {
            flow.setBandwidth(null);
        });
    }

    @Test
    public void setNegativeBandwidth() throws Exception {
        assertThrows(IllegalArgumentException.class,()-> {
            flow.setBandwidth(-1L);
        });
    }

    @Test
    public void setMeterId() throws Exception {
        flow.setMeterId(meterId);
        assertEquals(meterId, flow.getMeterId().longValue());
    }

    @Test
    public void setNegativeMeterId() throws Exception {
        assertThrows(IllegalArgumentException.class,()-> {
            flow.setMeterId(-1L);
        });
    }

    @Test
    public void setInputVlanId() throws Exception {
        flow.setInputVlanId(inputVlanId);
        assertEquals(inputVlanId, flow.getInputVlanId().intValue());
    }

    @Test
    public void setNullInputVlanId() throws Exception {
        flow.setInputVlanId(null);
        assertEquals(0, flow.getInputVlanId().intValue());
    }

    @Test
    public void setZeroInputVlanId() throws Exception {
        flow.setInputVlanId(0);
        assertEquals(0, flow.getInputVlanId().intValue());
    }

    @Test
    public void setNegativeInputVlanId() throws Exception {
        assertThrows(IllegalArgumentException.class,()-> {
            flow.setInputVlanId(-1);
        });
    }

    @Test
    public void setTooBigInputVlanId() throws Exception {
        assertThrows(IllegalArgumentException.class,()-> {
            flow.setInputVlanId(4096);
        });
    }
}
