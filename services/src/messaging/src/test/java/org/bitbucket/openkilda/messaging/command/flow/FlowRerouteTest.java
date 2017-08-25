package org.bitbucket.openkilda.messaging.command.flow;

import static org.bitbucket.openkilda.messaging.command.Constants.flowName;
import static org.bitbucket.openkilda.messaging.command.Constants.outputPort;
import static org.bitbucket.openkilda.messaging.command.Constants.switchId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public class FlowRerouteTest {
    @Test
    public void toStringTest() throws Exception {
        FlowReroute data = new FlowReroute();
        String dataString = data.toString();
        assertNotNull(dataString);
        assertFalse(dataString.isEmpty());
    }

    @Test
    public void switchId() throws Exception {
        FlowReroute data = new FlowReroute();
        data.setSwitchId(switchId);
        assertEquals(switchId, data.getSwitchId());
    }

    @Test
    public void portNo() throws Exception {
        FlowReroute data = new FlowReroute();
        data.setPortNo(outputPort);
        assertEquals(outputPort, data.getPortNo());
    }

    @Test
    public void flowId() throws Exception {
        FlowReroute data = new FlowReroute();
        data.setId(flowName);
        assertEquals(flowName, data.getId());
    }
}
