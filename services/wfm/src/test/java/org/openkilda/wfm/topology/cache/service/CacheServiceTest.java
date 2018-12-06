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

package org.openkilda.wfm.topology.cache.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.flow.FlowInfoData;
import org.openkilda.messaging.info.flow.FlowOperation;
import org.openkilda.messaging.model.FlowDto;
import org.openkilda.messaging.model.FlowPairDto;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.TestSender;
import org.openkilda.wfm.share.cache.FlowCache;
import org.openkilda.wfm.share.cache.NetworkCache;
import org.openkilda.wfm.topology.cache.StreamType;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.UUID;

public class CacheServiceTest {

    private static final String THIRD_FLOW_ID = "third-flow";
    private static final FlowPairDto<FlowDto, FlowDto> THIRD_FLOW = new FlowPairDto<>(
            new FlowDto(THIRD_FLOW_ID, 10000, false, "", new SwitchId("ff:00"), 1, 2, new SwitchId("ff:00"), 1, 2),
            new FlowDto(THIRD_FLOW_ID, 10000, false, "", new SwitchId("ff:00"), 1, 2, new SwitchId("ff:00"), 1, 2));

    private CacheService cacheService;
    private TestSender sender;

    @Before
    public void init() {
        SwitchRepository switchRepository = mock(SwitchRepository.class);
        IslRepository islRepository = mock(IslRepository.class);
        FlowRepository flowRepository = mock(FlowRepository.class);

        RepositoryFactory repositoryFactory = mock(RepositoryFactory.class);
        when(repositoryFactory.createSwitchRepository()).thenReturn(switchRepository);
        when(repositoryFactory.createIslRepository()).thenReturn(islRepository);
        when(repositoryFactory.createFlowRepository()).thenReturn(flowRepository);

        cacheService = new CacheService(new NetworkCache(), new FlowCache(), repositoryFactory);

        sender = new TestSender();
    }

    @Test
    public void cacheReceivesFlowTopologyUpdatesAndSendsToTopologyEngine() throws Exception {
        String correlationId = UUID.randomUUID().toString();
        FlowInfoData data = new FlowInfoData(THIRD_FLOW.getLeft().getFlowId(),
                THIRD_FLOW, FlowOperation.CREATE, correlationId);

        cacheService.handleFlowEvent(data, sender, correlationId);

        List<Object> messages = getTeMessages();

        Assert.assertEquals(1, messages.size());

        Object message = messages.get(0);

        Assert.assertTrue(message instanceof InfoMessage);

        Assert.assertEquals(THIRD_FLOW, ((FlowInfoData) ((InfoMessage) message).getData()).getPayload());
    }

    private List<Object> getTeMessages() {
        return sender.listMessages(StreamType.TPE.toString());
    }
}
