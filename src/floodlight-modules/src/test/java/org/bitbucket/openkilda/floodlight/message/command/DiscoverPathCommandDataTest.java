package org.bitbucket.openkilda.floodlight.message.command;

import org.bitbucket.openkilda.messaging.command.discovery.DiscoverPathCommandData;
import org.junit.Test;

import static org.bitbucket.openkilda.floodlight.Constants.inputPort;
import static org.bitbucket.openkilda.floodlight.Constants.switchId;
import static org.junit.Assert.*;

/**
 * Created by atopilin on 10/04/2017.
 */
public class DiscoverPathCommandDataTest {
    @Test
    public void toStringTest() throws Exception {
        final DiscoverPathCommandData data = new DiscoverPathCommandData();
        data.setSrcPortNo(inputPort);
        String dataString = data.toString();
        assertNotNull(dataString);
        assertFalse(dataString.isEmpty());
    }

    @Test
    public void srcSwitchId() throws Exception {
        DiscoverPathCommandData data = new DiscoverPathCommandData();
        data.setSrcSwitchId(switchId);
        assertEquals(switchId, data.getSrcSwitchId());
    }

    @Test
    public void srcPortNo() throws Exception {
        DiscoverPathCommandData data = new DiscoverPathCommandData();
        data.setSrcPortNo(inputPort);
        assertEquals(inputPort, data.getSrcPortNo());
    }

    @Test
    public void dstSwitchId() throws Exception {
        DiscoverPathCommandData data = new DiscoverPathCommandData();
        data.setDstSwitchId(switchId);
        assertEquals(switchId, data.getDstSwitchId());
    }

}
