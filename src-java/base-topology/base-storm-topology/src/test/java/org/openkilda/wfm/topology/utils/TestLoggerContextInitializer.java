/* Copyright 2020 Telstra Open Source
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


package org.openkilda.wfm.topology.utils;

import static junit.framework.TestCase.assertEquals;

import org.junit.Test;


public class TestLoggerContextInitializer {

    @Test
    public void testGetCleanTopoName() {
        assertEquals("network", LoggerContextInitializer.getCleanTopoName("network"));
        assertEquals("network-blue", LoggerContextInitializer.getCleanTopoName("network-blue"));
        assertEquals("network_blue", LoggerContextInitializer.getCleanTopoName("network_blue"));
        assertEquals("network_blue", LoggerContextInitializer.getCleanTopoName("network_blue-1-1"));
        assertEquals("network_blue", LoggerContextInitializer.getCleanTopoName("network_blue-1-1"));
    }
}
