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

package org.openkilda.messaging.payload;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MeterPoolTest {

    @Test
    public void resourcePoolTest() {
        MeterPool pool = new MeterPool(1, 4);
        int first = pool.allocate();
        assertEquals(1, first);

        int second = pool.allocate();
        assertEquals(2, second);

        int third = pool.allocate();
        assertEquals(3, third);

        pool.deallocate(third);
        pool.deallocate(second);

        int fourth = pool.allocate();
        assertEquals(4, fourth);

        int fifth = pool.allocate();
        assertEquals(3, fifth);

        int sixth = pool.allocate();
        assertEquals(2, sixth);

        assertEquals(4, pool.dumpPool().size());
    }

    @Test(expected = ArrayIndexOutOfBoundsException.class)
    public void resourcePoolFullTest() {
        MeterPool pool = new MeterPool(1, 1);
        pool.allocate();
        pool.allocate();
    }
}
