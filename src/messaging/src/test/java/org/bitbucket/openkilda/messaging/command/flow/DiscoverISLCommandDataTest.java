package org.bitbucket.openkilda.messaging.command.flow;

import static org.bitbucket.openkilda.messaging.command.Constants.outputPort;
import static org.bitbucket.openkilda.messaging.command.Constants.switchId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import org.bitbucket.openkilda.messaging.command.discovery.DiscoverIslCommandData;

import org.junit.Test;

/**
 * Created by atopilin on 10/04/2017.
 */
public class DiscoverISLCommandDataTest {
    @Test
    public void toStringTest() throws Exception {
        final DiscoverIslCommandData data = new DiscoverIslCommandData();
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
