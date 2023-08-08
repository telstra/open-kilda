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
import static org.openkilda.messaging.command.Constants.flowName;
import static org.openkilda.messaging.command.Constants.inputPort;
import static org.openkilda.messaging.command.Constants.outputPort;
import static org.openkilda.messaging.command.Constants.switchId;
import static org.openkilda.messaging.command.Constants.transitEncapsulationId;
import static org.openkilda.messaging.command.Constants.transitEncapsulationType;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.UUID;

public class InstallTransitFlowTest {
    private InstallTransitFlow flow = new InstallTransitFlow(UUID.randomUUID(),
            flowName, 0L, switchId, inputPort, outputPort, transitEncapsulationId, transitEncapsulationType,
            false);

    @Test
    public void toStringTest() throws Exception {
        String flowString = flow.toString();
        Assertions.assertNotNull(flowString);
        Assertions.assertFalse(flowString.isEmpty());
    }

    @Test
    public void getTransitEncapsulationId() throws Exception {
        assertEquals(transitEncapsulationId, flow.getTransitEncapsulationId().intValue());
    }

    @Test
    public void setTransitEncapsulationId() throws Exception {
        flow.setTransitEncapsulationId(transitEncapsulationId);
        assertEquals(transitEncapsulationId, flow.getTransitEncapsulationId().intValue());
    }

    @Test
    public void setNullTransitEncapsulationId() {
        assertThrows(IllegalArgumentException.class,()-> {
            flow.setTransitEncapsulationId(null);
        });
    }

    @Test
    public void setZeroTransitEncapsulationId() {
        assertThrows(IllegalArgumentException.class,()-> {
            flow.setTransitEncapsulationId(0);
        });
    }

    @Test
    public void setTooBigTransitEncapsulationId() {
        assertThrows(IllegalArgumentException.class,()-> {
            flow.setTransitEncapsulationId(4096);
        });
    }
}
