package org.bitbucket.openkilda.messaging.command.discovery;

import static org.bitbucket.openkilda.messaging.command.Constants.inputPort;
import static org.bitbucket.openkilda.messaging.command.Constants.switchId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import org.bitbucket.openkilda.messaging.command.discovery.DiscoverPathCommandData;

import org.junit.Test;

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
