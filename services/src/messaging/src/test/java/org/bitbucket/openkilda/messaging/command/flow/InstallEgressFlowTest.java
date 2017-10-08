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

package org.bitbucket.openkilda.messaging.command.flow;

import static org.bitbucket.openkilda.messaging.command.Constants.flowName;
import static org.bitbucket.openkilda.messaging.command.Constants.inputPort;
import static org.bitbucket.openkilda.messaging.command.Constants.outputPort;
import static org.bitbucket.openkilda.messaging.command.Constants.outputVlanId;
import static org.bitbucket.openkilda.messaging.command.Constants.outputVlanType;
import static org.bitbucket.openkilda.messaging.command.Constants.switchId;
import static org.bitbucket.openkilda.messaging.command.Constants.transitVlanId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import org.bitbucket.openkilda.messaging.payload.flow.OutputVlanType;

import org.junit.Test;

public class InstallEgressFlowTest {
    private InstallEgressFlow flow = new InstallEgressFlow(0L, flowName, 0L, switchId, inputPort,
            outputPort, transitVlanId, outputVlanId, outputVlanType);

    @Test
    public void toStringTest() throws Exception {
        String flowString = flow.toString();
        assertNotNull(flowString);
        assertFalse(flowString.isEmpty());
    }

    @Test
    public void getOutputVlanType() throws Exception {
        assertEquals(outputVlanType, flow.getOutputVlanType());
    }

    @Test
    public void getOutputVlanId() throws Exception {
        assertEquals(outputVlanId, flow.getOutputVlanId().intValue());
    }

    @Test
    public void setOutputVlanType() throws Exception {
        flow.setOutputVlanId(outputVlanId);
        flow.setOutputVlanType(outputVlanType);
        assertEquals(outputVlanType, flow.getOutputVlanType());
    }

    @Test(expected = IllegalArgumentException.class)
    public void setNullOutputVlanType() throws Exception {
        flow.setOutputVlanType(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setInvalidOutputVlanType() throws Exception {
        flow.setOutputVlanType(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setIncorrectNoneOutputVlanType() throws Exception {
        flow.setOutputVlanId(outputVlanId);
        flow.setOutputVlanType(OutputVlanType.NONE);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setIncorrectPopOutputVlanType() throws Exception {
        flow.setOutputVlanId(outputVlanId);
        flow.setOutputVlanType(OutputVlanType.POP);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setIncorrectPushOutputVlanType() throws Exception {
        flow.setOutputVlanId(0);
        flow.setOutputVlanType(OutputVlanType.PUSH);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setIncorrectReplaceOutputVlanType() throws Exception {
        flow.setOutputVlanId(null);
        flow.setOutputVlanType(OutputVlanType.REPLACE);
    }

    @Test
    public void setOutputVlanId() throws Exception {
        flow.setOutputVlanId(outputVlanId);
        assertEquals(outputVlanId, flow.getOutputVlanId().intValue());
    }

    @Test
    public void setNullOutputVlanId() throws Exception {
        flow.setOutputVlanId(null);
        assertEquals(0, flow.getOutputVlanId().intValue());
    }

    @Test
    public void setZeroOutputVlanId() throws Exception {
        flow.setOutputVlanId(0);
        assertEquals(0, flow.getOutputVlanId().intValue());
    }

    @Test(expected = IllegalArgumentException.class)
    public void setNegativeOutputVlanId() throws Exception {
        flow.setOutputVlanId(-1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setTooBigOutputVlanId() throws Exception {
        flow.setOutputVlanId(4096);
    }
}
