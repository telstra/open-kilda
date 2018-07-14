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

package org.openkilda.wfm.topology.packetmon.data;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

public class TimeValueTest {

    private static final int WindowSize = 5;
    private TimeValue timeValues = new TimeValue(WindowSize);
    private long[][] sampleValues = {
            {1530058871, 1219425071},
            {1530058931, 1223990911},
            {1530058991, 1228449180},
            {1530059051, 1232932728},
            {1530059111, 1237574591}
    };

    @Before
    public void setup() {
        for (int i = 0; i < sampleValues.length; i++) {
            timeValues.add(sampleValues[i][0], sampleValues[i][1]);
        }
    }

    @Test
    public void getStats() {
        assertEquals(75623, timeValues.getStats().getMean(), 0);
        timeValues.add(1530059171, 1242358883);
        assertEquals(5, timeValues.getTimeValues().length);
        assertEquals(76533.21667, timeValues.getStats().getMean(), 1);
    }
}
