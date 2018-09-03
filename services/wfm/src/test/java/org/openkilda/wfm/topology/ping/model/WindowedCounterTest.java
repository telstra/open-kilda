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

package org.openkilda.wfm.topology.ping.model;

import org.junit.Assert;
import org.junit.Test;

public class WindowedCounterTest {
    @Test
    public void scenario1() {
        WindowedCounter counter = new WindowedCounter(3);

        Assert.assertEquals(0, counter.getCount());

        counter.increment();
        Assert.assertEquals(1, counter.getCount());
        counter.increment();
        Assert.assertEquals(2, counter.getCount());

        counter.slide();
        counter.slide();
        Assert.assertEquals(2, counter.getCount());

        counter.increment();
        Assert.assertEquals(3, counter.getCount());

        counter.slide();
        Assert.assertEquals(1, counter.getCount());
    }

    @Test
    public void windowSize1() {
        WindowedCounter counter = new WindowedCounter(1);

        counter.increment();
        Assert.assertEquals(1, counter.getCount());
        counter.increment();
        Assert.assertEquals(2, counter.getCount());

        counter.slide();
        Assert.assertEquals(0, counter.getCount());

        counter.increment();
        Assert.assertEquals(1, counter.getCount());
    }
}
