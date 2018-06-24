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

package org.openkilda.wfm.isl;

import static org.junit.Assert.assertEquals;

import org.openkilda.messaging.model.DiscoveryLink;
import org.openkilda.messaging.model.NetworkEndpoint;

import org.junit.Before;
import org.junit.Test;

public class DiscoveryLinkTest {

    private DiscoveryLink dn;
    private static final String switchName = "sw1";
    private static final int portNumber = 1;

    @Before
    public void setUp() throws Exception {
        dn = new DiscoveryLink(switchName, portNumber, 1, 10);
    }

    /**
     * Test the setup of a DiscoveryNode - ie initial state of foundIsl; and get/set function.
     */
    @Test
    public void testDefaultLinkDiscovered() {
        // initial state should be false
        assertEquals(false, dn.isActive());
    }

    @Test
    public void shouldLinkBeDiscoveredWhenDestinationIsSet() {
        dn.activate(new NetworkEndpoint("sw2", 2));
        assertEquals(true, dn.isActive());
    }

    /**
     * Verify the initial behavior of forlorn and the limit scenarios.
     */
    @Test
    public void forlorn() {
        int threshhold = 2;
        dn = new DiscoveryLink("sw1", 2, 0, threshhold);
        assertEquals("A DN starts out as not excluded", false, dn.isDiscoverySuspended());
        dn.fail();
        dn.fail();
        dn.fail();
        assertEquals("The DN should now be excluded", true, dn.isDiscoverySuspended());
    }

    /**
     * Test the basic functions of failures. Starts at zero, can be incremented and cleared.
     */
    @Test
    public void failure() {
        assertEquals(0, dn.getConsecutiveFailure());
        dn.fail();
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
        dn.success();
        assertEquals(1, dn.getConsecutiveSuccess());
        dn.clearConsecutiveSuccess();
        assertEquals(0, dn.getConsecutiveSuccess());
    }

    /**
     * isAttemptsLimitExceeded tests if the passed-in limit is less than the number of attempts.
     */
    @Test
    public void incAttempts() {
        assertEquals(0, dn.getAttempts());
        dn.incAttempts();
        int attemptLimit = 2;
        assertEquals(1, dn.getAttempts());
        assertEquals(false, dn.isAttemptsLimitExceeded(attemptLimit));
        dn.incAttempts();
        assertEquals(2, dn.getAttempts());
        assertEquals(false, dn.isAttemptsLimitExceeded(attemptLimit));
        dn.incAttempts();
        assertEquals(3, dn.getAttempts());
        assertEquals(true, dn.isAttemptsLimitExceeded(attemptLimit));
    }

    @Test
    public void renew() {
        dn.incAttempts();
        dn.tick();
        assertEquals(1, dn.getAttempts());
        assertEquals(1, dn.getTimeCounter());
        // renew clears both attempts and ticks
        dn.renew();
        assertEquals(0, dn.getAttempts());
        assertEquals(0, dn.getTimeCounter());
    }

    @Test
    public void logTick() {
        dn.tick();
        dn.timeToCheck();
        dn.resetTickCounter();
        assertEquals(0, dn.getTimeCounter());

    }
}
