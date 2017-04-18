package org.bitbucket.openkilda.floodlight.message.command;

import org.junit.Test;

import static org.bitbucket.openkilda.floodlight.Constants.outputPort;
import static org.bitbucket.openkilda.floodlight.Constants.switchId;
import static org.junit.Assert.*;

/**
 * Created by atopilin on 10/04/2017.
 */
public class DiscoverISLCommandDataTest {
    @Test
    public void toStringTest() throws Exception {
        String dataString = new DiscoverISLCommandData().withSwitchId(switchId).toString();
        assertNotNull(dataString);
        assertFalse(dataString.isEmpty());
    }

    @Test
    public void switchId() throws Exception {
        DiscoverISLCommandData data = new DiscoverISLCommandData();
        data.setSwitchId(switchId);
        assertEquals(switchId, data.getSwitchId());
    }

    @Test
    public void withSwitchId() throws Exception {
        DiscoverISLCommandData data = new DiscoverISLCommandData().withSwitchId(switchId);
        assertEquals(switchId, data.getSwitchId());
    }

    @Test
    public void portNo() throws Exception {
        DiscoverISLCommandData data = new DiscoverISLCommandData();
        data.setPortNo(outputPort);
        assertEquals(outputPort, data.getPortNo());
    }

    @Test
    public void withPortNo() throws Exception {
        DiscoverISLCommandData data = new DiscoverISLCommandData().withPortNo(outputPort);
        assertEquals(outputPort, data.getPortNo());
    }
}
