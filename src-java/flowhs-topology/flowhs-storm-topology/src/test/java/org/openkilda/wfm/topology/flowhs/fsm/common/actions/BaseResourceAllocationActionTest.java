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

package org.openkilda.wfm.topology.flowhs.fsm.common.actions;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import org.openkilda.model.SwitchId;
import org.openkilda.pce.PathComputer;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.flow.resources.ResourceAllocationException;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class BaseResourceAllocationActionTest {

    @Mock
    private FlowPathRepository flowPathRepository;

    @Mock
    private IslRepository islRepository;

    @Mock
    PersistenceManager persistenceManager;

    @Mock
    PathComputer pathComputer;

    @Mock
    FlowResourcesManager resourcesManager;

    @Mock
    FlowOperationsDashboardLogger dashboardLogger;

    @Mock
    RepositoryFactory repositoryFactory;

    @Test(expected = ResourceAllocationException.class)
    public void updateAvailableBandwidthFailsOnOverProvisionTest() throws ResourceAllocationException {
        when(persistenceManager.getRepositoryFactory()).thenReturn(repositoryFactory);
        when(repositoryFactory.createFlowPathRepository()).thenReturn(flowPathRepository);
        when(repositoryFactory.createIslRepository()).thenReturn(islRepository);
        when(flowPathRepository.getUsedBandwidthBetweenEndpoints(any(), eq(10), any(), eq(10)))
                .thenReturn(100L);
        when(islRepository.updateAvailableBandwidth(any(), eq(10), any(), eq(10), eq(100L)))
                .thenReturn(-1L);

        BaseResourceAllocationAction action = Mockito.mock(BaseResourceAllocationAction.class,
                Mockito.withSettings()
                        .useConstructor(persistenceManager, 3, 3, 3, pathComputer, resourcesManager, dashboardLogger)
                        .defaultAnswer(Mockito.CALLS_REAL_METHODS));

        action.updateAvailableBandwidth(new SwitchId(1000), 10, new SwitchId(1000), 10,
                0L, false);
    }

    @Test()
    public void updateAvailableBandwidthIgnorsOverProvisionTest() throws ResourceAllocationException {
        when(persistenceManager.getRepositoryFactory()).thenReturn(repositoryFactory);
        when(repositoryFactory.createFlowPathRepository()).thenReturn(flowPathRepository);
        when(repositoryFactory.createIslRepository()).thenReturn(islRepository);
        when(flowPathRepository.getUsedBandwidthBetweenEndpoints(any(), eq(10), any(), eq(10)))
                .thenReturn(100L);
        when(islRepository.updateAvailableBandwidth(any(), eq(10), any(), eq(10), eq(100L)))
                .thenReturn(-1L);

        BaseResourceAllocationAction action = Mockito.mock(BaseResourceAllocationAction.class,
                Mockito.withSettings()
                        .useConstructor(persistenceManager, 3, 3, 3, pathComputer, resourcesManager, dashboardLogger)
                        .defaultAnswer(Mockito.CALLS_REAL_METHODS));

        action.updateAvailableBandwidth(new SwitchId(1000), 10, new SwitchId(1000), 10,
                0L, true);
    }
}
