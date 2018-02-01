package org.openkilda.wfm.isl;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class DiscoveryNodeTest {

    /** Will be created before each test */
    private DiscoveryNode dn;
    private static final String switchName = "sw1";
    private static final String portName = "1";

    @Before
    public void setUp() throws Exception {
        dn = new DiscoveryNode(switchName, portName, 1, 10);
    }

    /**
     * Test the setup of a DiscoveryNode - ie initial state of foundIsl; and get/set function.
     */
    @Test
    public void setFoundIsl() {
        // initial state should be false
        assertEquals(false, dn.isFoundIsl());
    }

    @Test
    public void isFoundIsl() {
        // assert that getter/setter is working
        dn.setFoundIsl(true);
        assertEquals(true, dn.isFoundIsl());
    }

    /**
     * Verify the initial behavior of forlorn and the limit scenarios
     */
    @Test
    public void forlorn() {
        int threshhold = 2;
        dn = new DiscoveryNode("sw1", "s2", 0, threshhold);
        assertEquals("A DN starts out as not forlorn", false, dn.forlorn());
        dn.incConsecutiveFailure();
        dn.incConsecutiveFailure();
        dn.incConsecutiveFailure();
        assertEquals("The DN should now be forlorn", true, dn.forlorn());
    }

    /**
     * Test the basic functions of failures. Starts at zero, can be incremented and cleared.
     */
    @Test
    public void failure() {
        assertEquals(0, dn.getConsecutiveFailure());
        dn.incConsecutiveFailure();
        assertEquals(1, dn.getConsecutiveFailure());
        dn.clearConsecutiveFailure();
        assertEquals(0, dn.getConsecutiveFailure());
    }

    /**
     * Test the basic functions of success. Starts at zero, can be incremented and cleared.
     */
    @Test
    public void getConsecutiveSuccess() {
        assertEquals(0, dn.getConsecutiveSuccess());
        dn.incConsecutiveSuccess();
        assertEquals(1, dn.getConsecutiveSuccess());
        dn.clearConsecutiveSuccess();
        assertEquals(0, dn.getConsecutiveSuccess());
    }

    /**
     * maxAttempts tests if the passed-in limit is less than the number of attempts.
     */
    @Test
    public void incAttempts() {
        int attemptLimit = 2;
        assertEquals(0, dn.getAttempts());
        dn.incAttempts();
        assertEquals(1, dn.getAttempts());
        assertEquals(false, dn.maxAttempts(attemptLimit));
        dn.incAttempts();
        assertEquals(2, dn.getAttempts());
        assertEquals(false, dn.maxAttempts(attemptLimit));
        dn.incAttempts();
        assertEquals(3, dn.getAttempts());
        assertEquals(true, dn.maxAttempts(attemptLimit));
    }

    @Test
    public void renew() {
        dn.incAttempts();
        dn.incTick();
        assertEquals(1, dn.getAttempts());
        assertEquals(1, dn.getTicks());
        // renew clears both attempts and ticks
        dn.renew();
        assertEquals(0, dn.getAttempts());
        assertEquals(0, dn.getTicks());
    }

    @Test
    public void logTick() {
        dn.incTick();
        dn.timeToCheck();
        dn.resetTickCounter();
        assertEquals(0, dn.getTicks());

    }

    @Test
    public void getSwitchId() {
        assertEquals(switchName, dn.getSwitchId());
    }

    @Test
    public void getPortId() {
        assertEquals(portName, dn.getPortId());
    }
}