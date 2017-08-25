package org.bitbucket.openkilda.pce.provider.neo;

import static org.junit.Assert.*;

import org.bitbucket.openkilda.messaging.info.event.SwitchState;
import org.bitbucket.openkilda.messaging.model.Isl;
import org.bitbucket.openkilda.messaging.model.Switch;

import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Ignore
public class NeoDriverTest {
    private NeoDriver neoDriver = new NeoDriver();

    private final Switch sw1 = new Switch("sw1", "", "", "", SwitchState.ACTIVATED, "localhost");
    private final Switch sw1updated = new Switch("sw1", "", "", "", SwitchState.REMOVED, "");

    private final Switch sw2 = new Switch("sw2", "", "", "", SwitchState.ACTIVATED, "localhost");
    private final Switch sw3 = new Switch("sw3", "", "", "", SwitchState.ADDED, "remote");
    private final Switch sw4 = new Switch("sw4", "", "", "", SwitchState.ADDED, "remote");
    private final Switch sw5 = new Switch("sw5", "", "", "", SwitchState.REMOVED, "remote");

    private final Isl isl12 = new Isl("sw1", "sw2", 1, 2, 1L, 0L, 10);
    private final Isl isl21 = new Isl("sw2", "sw1", 2, 1, 1L, 0L, 10);
    private final Isl isl23 = new Isl("sw2", "sw3", 1, 2, 1L, 0L, 10);
    private final Isl isl32 = new Isl("sw3", "sw2", 2, 1, 1L, 0L, 10);

    private final Isl isl14 = new Isl("sw1", "sw4", 2, 1, 1L, 0L, 10);
    private final Isl isl41 = new Isl("sw4", "sw1", 1, 2, 1L, 0L, 10);
    private final Isl isl24 = new Isl("sw2", "sw4", 3, 2, 1L, 0L, 10);
    private final Isl isl42 = new Isl("sw4", "sw2", 2, 3, 1L, 0L, 10);

    private final Isl isl54 = new Isl("sw5", "sw4", 1, 3, 1L, 0L, 10);
    private final Isl isl45 = new Isl("sw4", "sw5", 3, 1, 1L, 0L, 10);
    private final Isl isl52 = new Isl("sw5", "sw2", 2, 4, 1L, 0L, 10);
    private final Isl isl25 = new Isl("sw2", "sw5", 4, 2, 1L, 0L, 10);
    private final Isl isl53 = new Isl("sw5", "sw3", 3, 1, 1L, 0L, 10);
    private final Isl isl35 = new Isl("sw3", "sw5", 1, 3, 1L, 0L, 10);

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
        neoDriver.createSwitch(sw1);
        Switch sw = neoDriver.getSwitch(sw1.getSwitchId());
        assertEquals(sw1, sw);

        sw = neoDriver.getSwitch(sw2.getSwitchId());
        assertNull(sw);
    }

    @Test
    public void createSwitch() throws Exception {
        neoDriver.createSwitch(sw1);
    }

    @Test
    public void deleteSwitch() throws Exception {
        neoDriver.createSwitch(sw1);
        neoDriver.deleteSwitch(sw1.getSwitchId());
    }

    @Test
    public void updateSwitch() throws Exception {
        neoDriver.createSwitch(sw1);
        neoDriver.updateSwitch(sw1.getSwitchId(), sw1updated);
    }

    @Test
    public void dumpSwitches() throws Exception {
        neoDriver.createSwitch(sw1);
        neoDriver.createSwitch(sw2);
        Set<Switch> switches = neoDriver.dumpSwitches();
        assertEquals(new HashSet<>(Arrays.asList(sw1, sw2)), switches);
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