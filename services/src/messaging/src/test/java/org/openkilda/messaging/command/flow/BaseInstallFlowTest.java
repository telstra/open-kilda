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

import static org.junit.Assert.assertEquals;
import static org.openkilda.messaging.command.Constants.flowName;
import static org.openkilda.messaging.command.Constants.inputPort;
import static org.openkilda.messaging.command.Constants.outputPort;
import static org.openkilda.messaging.command.Constants.switchId;

import org.openkilda.model.SwitchId;

import org.junit.Test;

public class BaseInstallFlowTest {
    private static BaseInstallFlow flow = new BaseInstallFlow(0L, flowName, 0L, switchId, inputPort, outputPort);

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

    @Test(expected = IllegalArgumentException.class)
    public void setNullFlowName() {
        flow.setId(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setNullSwitchId() {
        flow.setSwitchId(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setIncorrectSwitchId() {
        flow.setSwitchId(new SwitchId(""));
    }

    @Test(expected = IllegalArgumentException.class)
    public void setNullInputPort() {
        flow.setInputPort(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setNullOutputPort() {
        flow.setOutputPort(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setNegativeInputPort() {
        flow.setInputPort(-1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setNegativeOutputPort() {
        flow.setOutputPort(-1);
    }
}
