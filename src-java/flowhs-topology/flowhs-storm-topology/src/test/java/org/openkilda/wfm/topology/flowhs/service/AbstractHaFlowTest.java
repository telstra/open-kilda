/* Copyright 2023 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.service;

import static org.mockito.Mockito.mock;

import org.openkilda.pce.PathComputer;
import org.openkilda.persistence.inmemory.InMemoryGraphBasedTest;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;

import org.junit.Before;
import org.mockito.Mock;

public abstract class AbstractHaFlowTest<T> extends InMemoryGraphBasedTest {
    @Mock
    protected FlowResourcesManager flowResourcesManager;
    @Mock
    protected RuleManager ruleManager;
    @Mock
    protected PathComputer pathComputer;

    @Before
    public void before() {
        ruleManager = mock(RuleManager.class);
        pathComputer = mock(PathComputer.class);
        flowResourcesManager = mock(FlowResourcesManager.class);
    }
}
