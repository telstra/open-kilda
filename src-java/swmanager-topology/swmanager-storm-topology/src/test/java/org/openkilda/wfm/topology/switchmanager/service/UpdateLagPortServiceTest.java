/* Copyright 2022 Telstra Open Source
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

package org.openkilda.wfm.topology.switchmanager.service;

import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.swmanager.request.UpdateLagPortRequest;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.tx.TransactionManager;
import org.openkilda.wfm.topology.switchmanager.error.InconsistentDataException;
import org.openkilda.wfm.topology.switchmanager.error.SwitchNotFoundException;
import org.openkilda.wfm.topology.switchmanager.service.handler.LagPortUpdateHandler;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;

@RunWith(MockitoJUnitRunner.class)
public class UpdateLagPortServiceTest {
    @Mock
    private SwitchManagerCarrier carrier;

    @Mock
    private LagPortOperationService operationService;

    @Mock
    RepositoryFactory repositoryFactory;

    @Mock
    TransactionManager transactionManager;

    @Test
    public void testKeepHandlerOnRequestKeyCollision() {
        LagPortOperationConfig config = newConfig();
        UpdateLagPortService subject = new UpdateLagPortService(carrier, operationService);

        String requestKey = "test-key";
        Assert.assertFalse(subject.activeHandlers.containsKey(requestKey));

        UpdateLagPortRequest request = new UpdateLagPortRequest(
                new SwitchId(1), (int) config.getPoolConfig().getIdMinimum(), Arrays.asList(1, 2, 3));
        subject.update(requestKey, request);
        LagPortUpdateHandler origin = subject.activeHandlers.get(requestKey);
        Assert.assertNotNull(origin);

        UpdateLagPortRequest request2 = new UpdateLagPortRequest(
                new SwitchId(2), (int) config.getPoolConfig().getIdMinimum(), Arrays.asList(1, 2, 3));
        Assert.assertThrows(InconsistentDataException.class, () -> subject.update(requestKey, request2));
        Assert.assertSame(origin, subject.activeHandlers.get(requestKey));
    }

    @Test
    public void testHandlerRemoveOnException() {
        LagPortOperationConfig config = newConfig();
        UpdateLagPortService subject = new UpdateLagPortService(carrier, operationService);

        SwitchId switchId = new SwitchId(1);
        Mockito.when(operationService.getSwitchIpAddress(switchId)).thenThrow(new SwitchNotFoundException(switchId));

        String requestKey = "test-key";
        UpdateLagPortRequest request = new UpdateLagPortRequest(
                switchId, (int) config.getPoolConfig().getIdMinimum(), Arrays.asList(1, 2, 3));
        subject.update(requestKey, request);
        Mockito.verify(carrier).errorResponse(
                Mockito.eq(requestKey), Mockito.eq(ErrorType.NOT_FOUND), Mockito.anyString(), Mockito.anyString());
        Assert.assertFalse(subject.activeHandlers.containsKey(requestKey));
    }

    private LagPortOperationConfig newConfig() {
        return new LagPortOperationConfig(
                repositoryFactory, transactionManager, 1000, 1999, 2000, 2999, 10, 100);
    }
}
