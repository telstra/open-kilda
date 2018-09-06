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

package org.openkilda.wfm.topology.stats;

import org.openkilda.wfm.AbstractStormTest;
import org.openkilda.wfm.topology.OutputCollectorMock;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class IslStatsTopologyTest extends AbstractStormTest {
    private long messagesExpected;
    private long messagesReceived;

    @Mock
    private TopologyContext topologyContext;
    private OutputCollectorMock outputCollectorMock = new OutputCollectorMock();
    private OutputCollector outputCollector = new OutputCollector(outputCollectorMock);

    // Leaving these here as a tickler if needed.
    @Before
    public void setupEach() {
    }

    @After
    public void teardownEach() {
    }

    @Test
    public void islStatsTopologyTest() throws Exception {

    }
}
