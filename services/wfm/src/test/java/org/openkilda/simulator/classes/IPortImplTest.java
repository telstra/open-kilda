/* Copyright 2018 Telstra Open Source
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

package org.openkilda.simulator.classes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class IPortImplTest {
    IPortImpl port;
    ISwitchImpl sw;
    int portNum = 1;

    @Rule
    public ExpectedException thrown = ExpectedException.none();


    @Before
    public void setUp() throws Exception {
        sw = new ISwitchImpl();
        port = new IPortImpl(sw, PortStateType.DOWN, portNum);
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testInit() throws Exception {
        int portNum = 5;
        IPortImpl port = new IPortImpl(sw, PortStateType.DOWN, portNum);
        assertFalse(port.isActive());
        assertEquals(portNum, port.getNumber());

        port = new IPortImpl(sw, PortStateType.UP, portNum);
        assertTrue(port.isActive());
    }

    @Test
    public void testEnableDisable() throws Exception {
        port.enable();
        assertTrue(port.isActive());

        port.disable();
        assertFalse(port.isActive());
    }

    @Test
    public void testBlockUnblock() throws Exception {
        port.block();
        assertFalse(port.isForwarding());

        port.unblock();
        assertTrue(port.isForwarding());
    }

    @Test
    public void isActiveIsl() throws Exception {
        port.enable();
        port.setPeerSwitch("00:00:00:00:00:01");
        port.setPeerPortNum(10);
        assertTrue(port.isActiveIsl());

        port.disable();
        assertFalse(port.isActiveIsl());

        port.enable();
        port.block();
        assertFalse(port.isActiveIsl());

        port.unblock();
        assertTrue(port.isActiveIsl());

        port.disable();
        port.unblock();
        assertFalse(port.isActiveIsl());
    }

    @Test
    public void getStats() throws Exception {
    }

}
