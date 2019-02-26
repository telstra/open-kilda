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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import org.openkilda.messaging.command.discovery.DiscoverIslCommandData;
import org.openkilda.messaging.info.event.IslChangeType;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.topology.discovery.model.Endpoint;

import lombok.Data;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PollIntegrationTest {
    private static final Integer taskId = 0;

    @Mock
    IDecisionMakerCarrier carrier;

    @Before
    public void setup() {
        reset(carrier);
    }

    @Test
    public void happyPath() {
        DiscoveryWatchListService watchListService = new DiscoveryWatchListService(10);
        DiscoveryWatcherService watcherService = new DiscoveryWatcherService(100, taskId);
        DiscoveryDecisionMakerService decisionMakerService = new DiscoveryDecisionMakerService(200, 100);

        final long latency = 100L;
        final long speed = 100000L;
        IntegrationCarrier integrationCarrier = new IntegrationCarrier(watcherService, watchListService,
                                                                       decisionMakerService);
        IWatcherCarrier watcherCarrier = mock(IWatcherCarrier.class);
        integrationCarrier.setWatcherCarrier(watcherCarrier);
        integrationCarrier.setDecisionMakerCarrier(carrier);

        // should produce discovery request
        Endpoint endpoint = Endpoint.of(new SwitchId(1), 1);
        watchListService.addWatch(integrationCarrier, endpoint, 1);

        ArgumentCaptor<DiscoverIslCommandData> discoveryRequestCatcher = ArgumentCaptor.forClass(
                DiscoverIslCommandData.class);
        verify(watcherCarrier).sendDiscovery(discoveryRequestCatcher.capture());

        DiscoverIslCommandData request = discoveryRequestCatcher.getValue();
        Assert.assertEquals(endpoint.getDatapath(), request.getSwitchId());
        Assert.assertEquals(endpoint.getPortNumber(), request.getPortNumber());

        // should process discovery response
        IslInfoData response = IslInfoData.builder().latency(latency)
                .source(new PathNode(request.getSwitchId(),
                        request.getPortNumber(), 0))
                .destination(new PathNode(new SwitchId(10), 10, 0))
                .state(IslChangeType.DISCOVERED)
                .speed(speed).underMaintenance(false)
                .packetId(request.getPacketId())
                .build();

        watcherService.confirmation(Endpoint.of(request.getSwitchId(), request.getPortNumber()), request.getPacketId());
        watcherService.discovery(integrationCarrier, response);

        IslInfoData expectedDiscoveryEvent = IslInfoData.builder().latency(latency)
                .source(new PathNode(new SwitchId(1), 1, 0))
                .destination(new PathNode(new SwitchId(10), 10, 0))
                .state(IslChangeType.DISCOVERED)
                .speed(speed)
                .underMaintenance(false)
                .packetId(0L)
                .build();
        verify(carrier).linkDiscovered(eq(expectedDiscoveryEvent));
    }

    @Test
    public void failed() {
        DiscoveryWatchListService watchListService = new DiscoveryWatchListService(10);
        DiscoveryWatcherService watcherService = new DiscoveryWatcherService(100, taskId);
        DiscoveryDecisionMakerService decisionMakerService = new DiscoveryDecisionMakerService(200, 100);

        IntegrationCarrier integrationCarrier = new IntegrationCarrier(watcherService, watchListService,
                                                                       decisionMakerService) {
            @Override
            public void sendDiscovery(DiscoverIslCommandData discoveryRequest) {
                watcherService.confirmation(
                        Endpoint.of(discoveryRequest.getSwitchId(), discoveryRequest.getPortNumber()),
                        discoveryRequest.getPacketId());
            }
        };
        integrationCarrier.setDecisionMakerCarrier(carrier);

        watchListService.addWatch(integrationCarrier, Endpoint.of(new SwitchId(1), 1), 0);

        for (int i = 1; i <= 200; ++i) {
            watchListService.tick(integrationCarrier, i);
            watcherService.tick(integrationCarrier, i);
        }

        verify(carrier).linkDestroyed(eq(Endpoint.of(new SwitchId(1), 1)));
    }

    @Data
    static class IntegrationCarrier implements IWatchListCarrier, IWatcherCarrier, IDecisionMakerCarrier {

        protected final DiscoveryWatcherService watcherService;
        protected final DiscoveryWatchListService watchListService;
        protected final DiscoveryDecisionMakerService decisionMakerService;

        protected IWatchListCarrier watchListCarrier;
        protected IWatcherCarrier watcherCarrier;
        protected IDecisionMakerCarrier decisionMakerCarrier;

        public IntegrationCarrier(DiscoveryWatcherService watcherService,
                                  DiscoveryWatchListService watchListService,
                                  DiscoveryDecisionMakerService decisionMakerService) {
            this.watcherService = watcherService;
            this.watchListService = watchListService;
            this.decisionMakerService = decisionMakerService;

            watchListCarrier = this;
            watcherCarrier = this;
            decisionMakerCarrier = this;
        }

        @Override
        public void watchRemoved(Endpoint endpoint) {
            // dummy, no need implementation
        }

        @Override
        public void discoveryRequest(Endpoint endpoint, long currentTime) {
            watcherService.addWatch(watcherCarrier, endpoint, currentTime);
        }

        @Override
        public void discoveryReceived(Endpoint endpoint, IslInfoData discoveryEvent, long currentTime) {
            decisionMakerService.discovered(decisionMakerCarrier, endpoint, discoveryEvent, currentTime);
        }

        @Override
        public void discoveryFailed(Endpoint endpoint, long currentTime) {
            decisionMakerService.failed(decisionMakerCarrier, endpoint, currentTime);
        }

        @Override
        public void sendDiscovery(DiscoverIslCommandData discoveryRequest) {
            // dummy, no need implementation
        }

        @Override
        public void clearDiscovery(Endpoint endpoint) {

        }

        @Override
        public void linkDiscovered(IslInfoData discoveryEvent) {
            // dummy, no need implementation
        }

        @Override
        public void linkDestroyed(Endpoint endpoint) {
            // dummy, no need implementation
        }
    }
}
