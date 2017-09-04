package org.bitbucket.openkilda.pce.provider.neo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.bitbucket.openkilda.messaging.info.event.SwitchInfoData;
import org.bitbucket.openkilda.messaging.info.event.SwitchState;
import org.bitbucket.openkilda.pce.NetworkTopologyConstants;

import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Ignore
public class NeoDriverTest {
    private final NeoDriver neoDriver = new NeoDriver();
    private final SwitchInfoData sw1updated = new SwitchInfoData("sw1", SwitchState.REMOVED, "", "", "", "");

    @After
    public void tearDown() {
        neoDriver.clean();
    }

    @Test
    public void getFlow() throws Exception {
    }

    @Test
    public void createFlow() throws Exception {
    }

    @Test
    public void deleteFlow() throws Exception {
    }

    @Test
    public void updateFlow() throws Exception {
    }

    @Test
    public void dumpFlows() throws Exception {
    }

    @Test
    public void getSwitch() throws Exception {
        neoDriver.createSwitch(NetworkTopologyConstants.sw1);
        SwitchInfoData sw = neoDriver.getSwitch(NetworkTopologyConstants.sw1.getSwitchId());
        assertEquals(NetworkTopologyConstants.sw1, sw);

        sw = neoDriver.getSwitch(NetworkTopologyConstants.sw2.getSwitchId());
        assertNull(sw);
    }

    @Test
    public void createSwitch() throws Exception {
        neoDriver.createSwitch(NetworkTopologyConstants.sw1);
    }

    @Test
    public void deleteSwitch() throws Exception {
        neoDriver.createSwitch(NetworkTopologyConstants.sw1);
        neoDriver.deleteSwitch(NetworkTopologyConstants.sw1.getSwitchId());
    }

    @Test
    public void updateSwitch() throws Exception {
        neoDriver.createSwitch(NetworkTopologyConstants.sw1);
        neoDriver.updateSwitch(NetworkTopologyConstants.sw1.getSwitchId(), sw1updated);
    }

    @Test
    public void dumpSwitches() throws Exception {
        neoDriver.createSwitch(NetworkTopologyConstants.sw1);
        neoDriver.createSwitch(NetworkTopologyConstants.sw2);
        Set<SwitchInfoData> switches = neoDriver.dumpSwitches();
        assertEquals(new HashSet<>(Arrays.asList(NetworkTopologyConstants.sw1,
                NetworkTopologyConstants.sw2)), switches);
    }

    @Test
    public void getIsl() throws Exception {
    }

    @Test
    public void createIsl() throws Exception {
    }

    @Test
    public void deleteIsl() throws Exception {
    }

    @Test
    public void updateIsl() throws Exception {
    }

    @Test
    public void dumpIsls() throws Exception {
    }

    @Test
    public void getPath() throws Exception {
    }

    @Test
    public void updatePathBandwidth() throws Exception {
    }

    @Test
    public void withNetwork() throws Exception {
    }

    @Test
    public void getWeight() throws Exception {
    }

}