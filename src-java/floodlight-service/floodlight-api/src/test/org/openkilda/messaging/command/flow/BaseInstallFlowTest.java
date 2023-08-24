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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.openkilda.messaging.command.Constants.flowName;
import static org.openkilda.messaging.command.Constants.inputPort;
import static org.openkilda.messaging.command.Constants.outputPort;
import static org.openkilda.messaging.command.Constants.switchId;

import org.openkilda.model.SwitchId;

import org.junit.jupiter.api.Test;

import java.util.UUID;

public class BaseInstallFlowTest {
    private static BaseInstallFlow flow = new BaseInstallFlow(UUID.randomUUID(), flowName, 0L, switchId,
            inputPort, outputPort, false);

    @Test
    public void getFlowName() {
        assertEquals(flowName, flow.getId());
    }

    @Test
    public void getSwitchId() {
        assertEquals(switchId, flow.getSwitchId());
    }

    @Test
    public void getInputPort() {
        assertEquals(inputPort, flow.getInputPort().intValue());
    }

    @Test
    public void getOutputPort() {
        assertEquals(outputPort, flow.getOutputPort().intValue());
    }

    @Test
    public void setFlowName() {
        flow.setId(flowName);
        assertEquals(flowName, flow.getId());
    }

    @Test
    public void setSwitchId() {
        flow.setSwitchId(switchId);
        assertEquals(switchId, flow.getSwitchId());
    }

    @Test
    public void setInputPort() {
        flow.setInputPort(inputPort);
        assertEquals(inputPort, flow.getInputPort().intValue());
    }

    @Test
    public void setOutputPort() {
        flow.setOutputPort(outputPort);
        assertEquals(outputPort, flow.getOutputPort().intValue());
    }

    @Test
    public void setNullFlowName() {
        assertThrows(IllegalArgumentException.class,()->{
            flow.setId(null);
        });
    }

    @Test
    public void setNullSwitchId() {
        assertThrows(IllegalArgumentException.class,()-> {
            flow.setSwitchId(null);
        });
    }

    @Test
    public void setIncorrectSwitchId() {
        assertThrows(IllegalArgumentException.class,()-> {
            flow.setSwitchId(new SwitchId(""));
        });
    }

    @Test
    public void setNullInputPort() {
        assertThrows(IllegalArgumentException.class,()-> {
            flow.setInputPort(null);
        });
    }

    @Test
    public void setNullOutputPort() {
        assertThrows(IllegalArgumentException.class,()-> {
            flow.setOutputPort(null);
        });
    }

    @Test
    public void setNegativeInputPort() {
        assertThrows(IllegalArgumentException.class,()-> {
            flow.setInputPort(-1);
        });
    }

    @Test
    public void setNegativeOutputPort() {
        assertThrows(IllegalArgumentException.class,()-> {
            flow.setOutputPort(-1);
        });
    }
}
