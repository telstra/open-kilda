package org.bitbucket.openkilda.floodlight.message.command;

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
        String dataString = new DiscoverPathCommandData().withSrcPortNo(inputPort).toString();
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
    public void withSrcSwitchId() throws Exception {
        DiscoverPathCommandData data = new DiscoverPathCommandData().withSrcSwitchId(switchId);
        assertEquals(switchId, data.getSrcSwitchId());
    }

    @Test
    public void srcPortNo() throws Exception {
        DiscoverPathCommandData data = new DiscoverPathCommandData();
        data.setSrcPortNo(inputPort);
        assertEquals(inputPort, data.getSrcPortNo());
    }

    @Test
    public void withSrcPortNo() throws Exception {
        DiscoverPathCommandData data = new DiscoverPathCommandData().withSrcPortNo(inputPort);
        assertEquals(inputPort, data.getSrcPortNo());
    }

    @Test
    public void dstSwitchId() throws Exception {
        DiscoverPathCommandData data = new DiscoverPathCommandData();
        data.setDstSwitchId(switchId);
        assertEquals(switchId, data.getDstSwitchId());
    }

    @Test
    public void withDstSwitchId() throws Exception {
        DiscoverPathCommandData data = new DiscoverPathCommandData().withDstSwitchId(switchId);
        assertEquals(switchId, data.getDstSwitchId());
    }
}
