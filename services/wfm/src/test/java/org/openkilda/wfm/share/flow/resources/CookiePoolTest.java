/* Copyright 2019 Telstra Open Source
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

package org.openkilda.wfm.share.flow.resources;

import static java.lang.String.format;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.openkilda.wfm.Neo4jBasedTest;

import org.junit.Before;
import org.junit.Test;

public class CookiePoolTest extends Neo4jBasedTest {

    private CookiePool cookiePool;

    @Before
    public void setUp() {
        cookiePool = new CookiePool(persistenceManager, 5, 25);
    }

    @Test
    public void cookiePool() {
        long first = cookiePool.allocate("flow_1");
        assertEquals(5, first);

        long second = cookiePool.allocate("flow_2");
        assertEquals(6, second);

        long third = cookiePool.allocate("flow_3");
        assertEquals(7, third);

        cookiePool.deallocate(second);
        long fourth = cookiePool.allocate("flow_4");
        assertEquals(6, fourth);

        long fifth = cookiePool.allocate("flow_5");
        assertEquals(8, fifth);
    }

    @Test(expected = ResourceNotAvailableException.class)
    public void cookiePoolFullTest() {
        for (int i = 4; i <= 26; i++) {
            assertTrue(cookiePool.allocate(format("flow_%d", i)) > 0);
        }
    }
}
