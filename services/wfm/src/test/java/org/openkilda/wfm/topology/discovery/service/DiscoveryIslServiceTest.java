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

package org.openkilda.wfm.topology.discovery.service;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.TransactionCallbackWithoutResult;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.wfm.topology.discovery.model.Endpoint;
import org.openkilda.wfm.topology.discovery.model.IslReference;

import org.junit.Before;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import java.util.Optional;

@RunWith(MockitoJUnitRunner.class)
public class DiscoveryIslServiceTest {
    private final Endpoint endpointAlpha1 = Endpoint.of(new SwitchId(1), 1);
    private final Endpoint endpointBeta2 = Endpoint.of(new SwitchId(2), 2);

    @Mock
    private IIslCarrier carrier;

    @Mock
    private PersistenceManager persistenceManager;

    @Mock
    private TransactionManager transactionManager;

    @Mock
    private RepositoryFactory repositoryFactory;

    @Mock
    private IslRepository islRepository;

    private DiscoveryIslService service;

    @Before
    public void setUp() throws Exception {
        when(persistenceManager.getTransactionManager()).thenReturn(transactionManager);
        when(persistenceManager.getRepositoryFactory()).thenReturn(repositoryFactory);

        when(repositoryFactory.createIslRepository()).thenReturn(islRepository);

        doAnswer((Answer) invocation -> {
            TransactionCallbackWithoutResult tr = invocation.getArgument(0);
            tr.doInTransaction();
            return null;
        }).when(transactionManager).doInTransaction(Mockito.any(TransactionCallbackWithoutResult.class));

        service = new DiscoveryIslService(persistenceManager);
    }

    /*@Test*/
    private void islMove() {
        when(islRepository.findByEndpoints(endpointAlpha1.getDatapath(), endpointAlpha1.getPortNumber(),
                                           endpointBeta2.getDatapath(), endpointBeta2.getPortNumber()))
             .thenReturn(Optional.empty());

        IslReference ref = new IslReference(endpointAlpha1, endpointBeta2);
        service.islMove(carrier, ref.getSource(), ref);

        System.out.println(mockingDetails(carrier).printInvocations());
        System.out.println(mockingDetails(islRepository).printInvocations());
    }
}
