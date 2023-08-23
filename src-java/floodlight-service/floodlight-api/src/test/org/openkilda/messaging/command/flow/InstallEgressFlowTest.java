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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.openkilda.messaging.command.Constants.flowName;
import static org.openkilda.messaging.command.Constants.inputPort;
import static org.openkilda.messaging.command.Constants.outputPort;
import static org.openkilda.messaging.command.Constants.outputVlanId;
import static org.openkilda.messaging.command.Constants.outputVlanType;
import static org.openkilda.messaging.command.Constants.switchId;
import static org.openkilda.messaging.command.Constants.transitEncapsulationId;
import static org.openkilda.messaging.command.Constants.transitEncapsulationType;

import org.openkilda.model.OutputVlanType;

import org.junit.jupiter.api.Test;

import java.util.UUID;

public class InstallEgressFlowTest {
    private InstallEgressFlow flow = new InstallEgressFlow(UUID.randomUUID(), flowName, 0L, switchId, inputPort,
            outputPort, transitEncapsulationId, transitEncapsulationType, outputVlanId, outputVlanType,
            false);

    @Test
    public void toStringTest() {
        String flowString = flow.toString();
        assertNotNull(flowString);
        assertFalse(flowString.isEmpty());
    }

    @Test
    public void getOutputVlanType() {
        assertEquals(outputVlanType, flow.getOutputVlanType());
    }

    @Test
    public void getOutputVlanId() {
        assertEquals(outputVlanId, flow.getOutputVlanId().intValue());
    }

    @Test
    public void setOutputVlanType() {
        flow.setOutputVlanId(outputVlanId);
        flow.setOutputVlanType(outputVlanType);
        assertEquals(outputVlanType, flow.getOutputVlanType());
    }

    @Test
    public void setNullOutputVlanType() {
        assertThrows(IllegalArgumentException.class,()-> {
            flow.setOutputVlanType(null);
        });
    }

    @Test
    public void setInvalidOutputVlanType() {
        assertThrows(IllegalArgumentException.class,()-> {
            flow.setOutputVlanType(null);
        });
    }

    @Test
    public void setIncorrectNoneOutputVlanType() {
        assertThrows(IllegalArgumentException.class,()-> {
            flow.setOutputVlanId(outputVlanId);
            flow.setOutputVlanType(OutputVlanType.NONE);
        });
    }

    @Test
    public void setIncorrectPopOutputVlanType() {
        assertThrows(IllegalArgumentException.class,()-> {
            flow.setOutputVlanId(outputVlanId);
            flow.setOutputVlanType(OutputVlanType.POP);
        });
    }

    @Test
    public void setIncorrectPushOutputVlanType() {
        assertThrows(IllegalArgumentException.class,()-> {
            flow.setOutputVlanId(0);
            flow.setOutputVlanType(OutputVlanType.PUSH);
        });
    }

    @Test
    public void setIncorrectReplaceOutputVlanType() {
        assertThrows(IllegalArgumentException.class,()-> {
            flow.setOutputVlanId(null);
            flow.setOutputVlanType(OutputVlanType.REPLACE);
        });
    }

    @Test
    public void setOutputVlanId() {
        flow.setOutputVlanId(outputVlanId);
        assertEquals(outputVlanId, flow.getOutputVlanId().intValue());
    }

    @Test
    public void setNullOutputVlanId() {
        flow.setOutputVlanId(null);
        assertEquals(0, flow.getOutputVlanId().intValue());
    }

    @Test
    public void setZeroOutputVlanId() {
        flow.setOutputVlanId(0);
        assertEquals(0, flow.getOutputVlanId().intValue());
    }

    @Test
    public void setNegativeOutputVlanId() {
        assertThrows(IllegalArgumentException.class,()-> {
            flow.setOutputVlanId(-1);
        });
    }

    @Test
    public void setTooBigOutputVlanId() {
        assertThrows(IllegalArgumentException.class,()-> {
            flow.setOutputVlanId(4096);
        });
    }
}
